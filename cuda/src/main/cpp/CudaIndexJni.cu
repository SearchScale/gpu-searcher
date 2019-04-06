#include <stdio.h>
#include <math.h>
#include "CudaIndexJni.h"
#include <thrust/transform.h>
#include <thrust/functional.h>
#include <thrust/host_vector.h>
#include <thrust/device_vector.h>
#include <vector>
#include <sys/time.h>
  
using namespace std;
using namespace thrust;

long ms () {
  struct timeval tp;
  gettimeofday(&tp, NULL);
  return tp.tv_sec * 1000 + tp.tv_usec / 1000; //get current timestamp in milliseconds
}
  
long T;
long P;
long TOTAL_DOCS;

struct saxpy_functor {
  const float a;

  saxpy_functor(float _a) : a(_a) {}

  __host__ __device__
  float operator()(const float& x, const float& y) const { 
    return a * x + y;
  }
};

host_vector<int> startPositionsCpu;
device_vector<int> docIdsGpu;
device_vector<float> partialScoresGpu;
device_vector<int> docIdsTemplate;

JNIEXPORT jint JNICALL Java_CudaIndexJni_initIndex
(JNIEnv *env, jobject jobj, jintArray docIds, jfloatArray partialScores, jintArray startPositions, jlong totalDocs) {
  jsize len = env->GetArrayLength(startPositions);
  jsize numPostings = env->GetArrayLength(docIds);
  vector<int> docs (numPostings);
  env->GetIntArrayRegion( docIds, 0, numPostings, &docs[0] );
  vector<float> scores (numPostings);
  env->GetFloatArrayRegion( partialScores, 0, numPostings, &scores[0] );
  int *starts = env->GetIntArrayElements(startPositions, NULL);
  
  TOTAL_DOCS = totalDocs;
  cout<<"[CUDA] TOTAL_DOCS: "<<TOTAL_DOCS<<endl; 
  T = len;
  P = numPostings;

  for (int i=0; i<T; i++) {
    startPositionsCpu.push_back(starts[i]);
  }
  docIdsGpu = docs;
  partialScoresGpu = scores;

  host_vector<int> tmp(TOTAL_DOCS);
  for (int i=0; i<TOTAL_DOCS; i++)
    tmp[i] = i;
  docIdsTemplate = tmp;
  return T;
}

jobject algorithm2(JNIEnv *env, long mergedSize, vector<int> queryTerms, jint topK) {
  device_vector<int> mergedDocIds(mergedSize);
  device_vector<float> mergedPartialScores(mergedSize);
  device_vector<float> reducedValues(mergedSize);
  device_vector<int>   reducedKeys(mergedSize);

  int pos = 0;
  for (int q=0; q<queryTerms.size(); q++) {
    int n = startPositionsCpu[queryTerms[q]+1]-startPositionsCpu[queryTerms[q]];
    copy_n(device, docIdsGpu.begin() + (startPositionsCpu[queryTerms[q]]), n, mergedDocIds.begin() + pos);
    copy_n(device, partialScoresGpu.begin() + (startPositionsCpu[queryTerms[q]]), n, mergedPartialScores.begin() + pos);

    pos += n;
  }

  thrust::sort_by_key(mergedDocIds.begin(), mergedDocIds.end(), mergedPartialScores.begin());
  thrust::pair<device_vector<int>::iterator,device_vector<float>::iterator > p = reduce_by_key(device,
                  mergedDocIds.begin(), mergedDocIds.end(),
                  mergedPartialScores.begin(),
                  reducedKeys.begin(),
                  reducedValues.begin());
  thrust::sort_by_key(reducedValues.begin(), p.second, reducedKeys.begin(), thrust::greater<float>());

  cout<<"Size of merged docid: "<<mergedDocIds.size()<<endl;
  cout<<"Merged size: "<<mergedSize<<endl;

  long retSize = topK;
  if (topK < 0) {
    thrust::device_vector<float>::iterator boundary = thrust::find(
              thrust::device, reducedValues.begin(), reducedValues.end(), 0.0);
    thrust::distance(reducedValues.begin(), boundary);
  }
  int   *retDocsAndScores = (int*)malloc(retSize*4*2);
  float *distances = &((float*)retDocsAndScores)[retSize];

  thrust::copy(reducedKeys.begin(), reducedKeys.begin()+retSize, retDocsAndScores);
  thrust::copy(reducedValues.begin(), reducedValues.begin()+retSize, distances);

  jobject directBuffer = env->NewDirectByteBuffer((void*)retDocsAndScores, retSize*2*4);
  return directBuffer;
}

jobject algorithm1(JNIEnv *env, long mergedSize, vector<int> queryTerms, jint topK) {
  device_vector<float> result(TOTAL_DOCS);
  device_vector<int> retDocIdsGpu(TOTAL_DOCS);
  thrust::copy(docIdsTemplate.begin(), docIdsTemplate.end(), retDocIdsGpu.begin());

  for (int q=0; q<queryTerms.size(); q++) {
    device_vector<float> partial(TOTAL_DOCS);
    
    scatter(partialScoresGpu.begin() + (startPositionsCpu[queryTerms[q]]), partialScoresGpu.begin() +
        (startPositionsCpu[queryTerms[q]+1]), docIdsGpu.begin() + (startPositionsCpu[queryTerms[q]]), partial.begin());
    
    thrust::transform(partial.begin(), partial.end(), result.begin(), result.begin(), saxpy_functor(1));
  }
  thrust::sort_by_key(result.begin(), result.end(), retDocIdsGpu.begin(), thrust::greater<float>());
  cout<<result[0]<<" ("<<retDocIdsGpu[0]<<")"<<endl;
  cout<<result[1]<<" ("<<retDocIdsGpu[1]<<")"<<endl;
  cout<<result[2]<<" ("<<retDocIdsGpu[2]<<")"<<endl;

  long retSize = topK;
  if (topK < 0) {
    thrust::device_vector<float>::iterator boundary = thrust::find(
              thrust::device, result.begin(), result.end(), 0.0);
    thrust::distance(result.begin(), boundary);
  }
  int   *retDocsAndScores      = (int*)malloc(retSize*4*2);
  float *distances = &((float*)retDocsAndScores)[retSize];
  thrust::copy(retDocIdsGpu.begin(), retDocIdsGpu.begin() + retSize, retDocsAndScores);
  thrust::copy(result.begin(), result.begin() + retSize, distances);
  jobject directBuffer = env->NewDirectByteBuffer((void*)retDocsAndScores, retSize*2*4);
  return directBuffer;
}

JNIEXPORT jobject JNICALL Java_CudaIndexJni_getScores
  (JNIEnv *env, jobject jobj, jintArray terms, jint topK) {
  jsize Q = env->GetArrayLength(terms);
  vector<int> queryTerms (Q);
  env->GetIntArrayRegion( terms, 0, Q, &queryTerms[0] );

  cout<<"Initialized CUDA with terms "<<T<<" and query terms "<<Q<<endl;
  cout<<"Postings: "<<P<<endl;

  long mergedSize = 0;
  for (int q=0; q<queryTerms.size(); q++) {
    cout<<"Term: "<<q<<", start: "<<startPositionsCpu[queryTerms[q]]<<", end: "<<startPositionsCpu[queryTerms[q]+1]<<endl;
    mergedSize += startPositionsCpu[queryTerms[q]+1]-startPositionsCpu[queryTerms[q]];
  }
      
  cout<<"Merged size is going to be: "<<mergedSize<<endl;
 
  // TODO: Take some decision on which algorithm to use based on mergedSize and TOTAL_DOCS
  //jobject alg = algorithm1(env, mergedSize, queryTerms, topK);
  jobject alg = algorithm2(env, mergedSize, queryTerms, topK);
  return alg;
}



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

  host_vector<int> startPositionsCpu;
  device_vector<int> docIdsGpu;
  device_vector<float> partialScoresGpu;

  JNIEXPORT jint JNICALL Java_CudaIndexJni_initIndex
  (JNIEnv *env, jobject jobj, jintArray docIds, jfloatArray partialScores, jintArray startPositions) {
      jsize len = env->GetArrayLength(startPositions);
      jsize numPostings = env->GetArrayLength(docIds);
      vector<int> docs (numPostings);
      env->GetIntArrayRegion( docIds, 0, numPostings, &docs[0] );
      vector<float> scores (numPostings);
      env->GetFloatArrayRegion( partialScores, 0, numPostings, &scores[0] );
      int *starts = env->GetIntArrayElements(startPositions, NULL);
  
      T = len;
      P = numPostings;

      for (int i=0; i<T; i++) {
        startPositionsCpu.push_back(starts[i]);
      }
      docIdsGpu = docs;
      partialScoresGpu = scores;

		return T;
  }

JNIEXPORT jobject JNICALL Java_CudaIndexJni_getScores
  (JNIEnv *env, jobject jobj, jintArray terms)
  {
    jsize Q = env->GetArrayLength(terms);
    vector<int> queryTerms (Q);
    env->GetIntArrayRegion( terms, 0, Q, &queryTerms[0] );

    cout<<"Initialized CUDA with terms "<<T<<" and query terms "<<Q<<endl;
    cout<<"Postings: "<<P<<endl;

      int mergedSize = 0;
      for (int q=0; q<queryTerms.size(); q++) {
          mergedSize += startPositionsCpu[queryTerms[q]+1]-startPositionsCpu[queryTerms[q]];
      }
      
      cout<<"Merged size is going to be: "<<mergedSize<<endl;
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
      //t.stop(); cout<<"Time: "<<t.elapsed()/1000000.0<<endl;
  
      cout<<"Size of merged docid: "<<mergedDocIds.size()<<endl;

      cout<<"(CUDA) Doc "<<reducedKeys[0]<<": "<<"score="<<reducedValues[0]<<endl;
      cout<<"(CUDA) Doc "<<reducedKeys[1]<<": "<<"score="<<reducedValues[1]<<endl;
      cout<<"(CUDA) Doc "<<reducedKeys[2]<<": "<<"score="<<reducedValues[2]<<endl;

      cout<<"Merged size: "<<mergedSize<<endl;
      int   *retDocsAndScores      = (int*)malloc(mergedSize*4*2);
      float *distances = &((float*)retDocsAndScores)[mergedSize];
  
      thrust::copy(reducedKeys.begin(), reducedKeys.end(), retDocsAndScores);
      thrust::copy(reducedValues.begin(), reducedValues.end(), distances);
  
      jobject directBuffer = env->NewDirectByteBuffer((void*)retDocsAndScores, mergedSize*2*4);
  
      return directBuffer;
  }
  
  
  
  
  
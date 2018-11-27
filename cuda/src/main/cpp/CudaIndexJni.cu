

  #include <stdio.h>
  #include <math.h>
  #include "CudaIndexJni.h"
  
/*  #include <thrust/transform.h>
  #include <thrust/functional.h>
  #include <thrust/host_vector.h>
  #include <thrust/device_vector.h>*/
  #include <vector>
  #include <sys/time.h>
  
  using namespace std;
//  using namespace thrust;

  long ms () {
      struct timeval tp;
      gettimeofday(&tp, NULL);
      return tp.tv_sec * 1000 + tp.tv_usec / 1000; //get current timestamp in milliseconds
  }
  
  long N;
  
  /*host_vector<int> startPositionsGpu
  device_vector<int> docIdsGpu;
  device_vector<float> partialScoresGpu;*/

  JNIEXPORT jint JNICALL Java_CudaIndexJni_initIndex
  (JNIEnv *env, jobject jobj, jintArray docIds, jfloatArray partialScores, jintArray startPositions) {
      jsize len = env->GetArrayLength(startPositions);
      int *docs = env->GetIntArrayElements(docIds, NULL);
      float *scores = env->GetFloatArrayElements(partialScores, NULL);
      int *starts = env->GetIntArrayElements(startPositions, NULL);
  
      N = len;
  
      // Copy the vectors to the device
      /*host_vector<int> cpu_docs(N);
      for (int i=0; i<N; i++) cpu_docs[i] = docs[i];
      gpu_docs = cpu_docs;
  
      host_vector<float> cpu_lats(N);
      for (int i=0; i<N; i++) cpu_lats[i] = lats[i];
      gpu_lats = cpu_lats;
  
      host_vector<float> cpu_lngs(N);
      for (int i=0; i<N; i++) cpu_lngs[i] = lngs[i];
      gpu_lngs = cpu_lngs;*/

      /*startPositionsGpu = starts;
      docIdsGpu = docs;
      partialsScoresGpu = scores;
  
      cudaDeviceSynchronize();
      */return N;
  }

JNIEXPORT jobject JNICALL Java_CudaIndexJni_getScores
  (JNIEnv *env, jobject jobj, jintArray terms)
  {
  
      /*long timer = ms();
  
      // Actual scoring/sorting on device
      device_vector<float> gpu_distances(N);
      thrust::transform(gpu_lats.begin(), gpu_lats.end(), gpu_lngs.begin(), gpu_distances.begin(), geodist(lat, lng)  );
      cudaDeviceSynchronize();
      device_vector<int> docIds = gpu_docs;
      cout<<"Transformation applied: "<<endl;
      thrust::sort_by_key(gpu_distances.begin(), gpu_distances.end(), docIds.begin());
      cudaDeviceSynchronize();
      cout<<"Sorting done: "<<endl;
  
      int   *docs      = (int*)malloc(N*4*2);
      float *distances = &((float*)docs)[N];
  
      thrust::copy(docIds.begin(), docIds.end(), docs);
      thrust::copy(gpu_distances.begin(), gpu_distances.end(), distances);
  
      jobject directBuffer = env->NewDirectByteBuffer((void*)docs, N*2*4);
      cout<<"Cuda After array copy total time: "<<ms()-timer<<endl;
  
      return directBuffer;*/
      return NULL;
  }
  
  
  
  
  
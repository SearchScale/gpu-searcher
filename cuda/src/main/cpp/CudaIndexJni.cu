

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
      */return T;
  }

JNIEXPORT jobject JNICALL Java_CudaIndexJni_getScores
  (JNIEnv *env, jobject jobj, jintArray terms)
  {
    jsize Q = env->GetArrayLength(terms);
    vector<int> queryTerms (Q);
    env->GetIntArrayRegion( terms, 0, Q, &queryTerms[0] );

    /*for (int i=0; i<T; i++) {
        cout<<startPositionsCpu[i]<<", ";
    } cout<<endl;
  
    for (int i=0; i<P; i++) {
        cout<<docIdsGpu[i]<<"="<<partialScoresGpu[i]<<", ";
    }
    cout<<endl;*/
    cout<<"Initialized CUDA with terms "<<T<<" and query terms "<<Q<<endl;
    cout<<"Postings: "<<P<<endl;
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

      int mergedSize = 0;
      for (int q=0; q<queryTerms.size(); q++) {
          mergedSize += startPositionsCpu[queryTerms[q]+1]-startPositionsCpu[queryTerms[q]];
      }
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
  
      return NULL;
  }
  
  
  
  
  
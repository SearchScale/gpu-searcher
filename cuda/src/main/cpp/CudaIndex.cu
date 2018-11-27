#include <stdio.h>
#include <math.h>
#include "GpuGeoDist.h"

#include <thrust/transform.h>
#include <thrust/functional.h>
#include <thrust/host_vector.h>
#include <thrust/device_vector.h>
#include <vector>
#include <sys/time.h>

using namespace std;
using namespace thrust;

struct geodist {
	const float refX;
	const float refY;

	geodist(float _x, float _y): refX(_x), refY(_y) {}

	__host__ __device__ float operator()(float &x, float &y) const {
        return sqrt((x - refX) * (x-refX) + (y-refY)*(y-refY));
    }
};

long ms () {
	struct timeval tp;
	gettimeofday(&tp, NULL);
	return tp.tv_sec * 1000 + tp.tv_usec / 1000; //get current timestamp in milliseconds
}

int *docs;
float *lats;
float *lngs;
long N;

device_vector<int> gpu_docs;
device_vector<float> gpu_lats;
device_vector<float> gpu_lngs;

JNIEXPORT jint JNICALL Java_GpuGeoDist_initIndex(JNIEnv *env, jobject obj, jintArray docIdsArray, jfloatArray latsArray, jfloatArray lngsArray) {
    jsize len = env->GetArrayLength(docIdsArray);
    docs = env->GetIntArrayElements(docIdsArray, NULL);
    lats = env->GetFloatArrayElements(latsArray, NULL);
    lngs = env->GetFloatArrayElements(lngsArray, NULL);

    N = len;

    // Copy the vectors to the device
    host_vector<int> cpu_docs(N);
    for (int i=0; i<N; i++) cpu_docs[i] = docs[i];
    gpu_docs = cpu_docs;

    host_vector<float> cpu_lats(N);
    for (int i=0; i<N; i++) cpu_lats[i] = lats[i];
    gpu_lats = cpu_lats;

    host_vector<float> cpu_lngs(N);
    for (int i=0; i<N; i++) cpu_lngs[i] = lngs[i];
    gpu_lngs = cpu_lngs;

    cudaDeviceSynchronize();
    return N;
}


JNIEXPORT jobject JNICALL Java_GpuGeoDist_findNearest(JNIEnv *env, jobject obj, jfloat lat, jfloat lng)
{

    long timer = ms();

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

    return directBuffer;
}





gpu-searcher
============

GPU accelerated BM25 using Thrust and Cuda.

Running
-------

Install CUDA and NVIDIA drivers. Then, run the following:

    tar -xvf wikipedia.tar.bz2
    mvn package
    java -cp .:lucene/target/accelerated-searcher-1.0-jar-with-dependencies.jar WikipediaTest wikipedia.lucene

Once done once, the following files are generated and kept ready for the next run:

    docids.arr
    file.db
    scores.arr
    starts.arr
    term-dict.txt
    totaldocs.int

Next time, these files are used for performing the searches.

Sample output (on RTX 4090 and Ryzen 7950X):

    (base) ishan@4090-workstation:~/code/gpu-searcher$ java -cp .:lucene/target/accelerated-searcher-1.0-jar-with-dependencies.jar WikipediaTest wikipedia.lucene
    22.97202
    0.75
    1.2
    0$655256: 11.742765, 6$678716: 12.160792, 7$745150: 11.544346, 9$829395: 11.948123, 10$930434: 11.742765, 15$1196611: 11.742765, 37$313010: 13.873336, 38$189450: 18.48966, 40$864703: 12.664972, 50$1078765: 19.209423, 51$104143: 11.508395, 53$228620: 11.903997, 54$298354: 12.782815, 62$408522: 12.782815, 63$724116: 11.702853, 64$23537: 10.919697, 65$64200: 10.193917, 66$93991: 11.117583, 67$98371: 10.728732, 68$123458: 11.117583, 
    [CUDA] TOTAL_DOCS: 1249999
    Cuda searcher initialization took (ms): 3074.166161
    Specialized searcher was called... Term dictionary size: 1081008
    Query terms: [404790, 1069332, 837683, 36105, 253452]
    Initialized CUDA with terms 1081008 and query terms 5
    Postings: 25691436
    Term: 0, start: 10207319, end: 10242830
    Term: 1, start: 25472044, end: 25488196
    Term: 2, start: 20178106, end: 20178317
    Term: 3, start: 783891, end: 853120
    Term: 4, start: 6374762, end: 6384282
    Merged size is going to be: 130623
    Size of merged docid: 130623
    Merged size: 130623
    Java received array elements: java.nio.DirectByteBuffer[pos=0 lim=80 cap=80]
    Cuda searcher took: 9.018192
    Term resolution time: 0.076849
    (Java-CUDA) Total hits: 10
    (Java-CUDA) Scoredocs: 10
    (Java-CUDA) Doc: 691498, score: 17.90663
    (Java-CUDA) Doc: 1092523, score: 17.247147
    (Java-CUDA) Doc: 717381, score: 15.725261
    (Java-CUDA) Doc: 1038291, score: 14.75269
    (Java-CUDA) Doc: 544082, score: 14.693629
    (Java-CUDA) Doc: 1150380, score: 14.475551
    (Java-CUDA) Doc: 1094919, score: 14.364374
    (Java-CUDA) Doc: 1017824, score: 14.235897
    (Java-CUDA) Doc: 381649, score: 14.0797
    (Java-CUDA) Doc: 684984, score: 14.036088
    Lucene searcher took: 15.002052
    Hits: 126192
    doc=691498 score=17.906631 shardIndex=0
    doc=1092523 score=17.247147 shardIndex=0
    doc=717381 score=15.725261 shardIndex=0
    doc=1038291 score=14.75269 shardIndex=0
    doc=544082 score=14.693628 shardIndex=0
    doc=1150380 score=14.475551 shardIndex=0
    doc=1094919 score=14.364373 shardIndex=0
    doc=1017824 score=14.235896 shardIndex=0
    doc=381649 score=14.0797 shardIndex=0
    doc=684984 score=14.036089 shardIndex=0
    Test finished...
    terminate called after throwing an instance of 'thrust::THRUST_200301_520_NS::system::system_error'
      what():  CUDA free failed: cudaErrorCudartUnloading: driver shutting down
    Aborted (core dumped)


Note: The dataset was generated using WikipediaIndexer.
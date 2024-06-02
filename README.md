gpu-searcher
============

GPU accelerated BM25 using Thrust and Cuda.

Running
-------

Install CUDA and NVIDIA drivers. Then, run the following:

    tar -xvf wikipedia.tar.bz2
    mvn package
    java -cp .:lucene/target/accelerated-searcher-1.0-jar-with-dependencies.jar Gov2Test wikipedia.lucene

Once done once, the following files are generated and kept ready for the next run:

    docids.arr
    file.db
    scores.arr
    starts.arr
    term-dict.txt
    totaldocs.int

Next time, these files are used for performing the searches.

Note: The dataset was generated using WikipediaIndexer.

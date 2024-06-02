import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public class WikipediaIndexer {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static int COMMIT_FREQ = 500000;
  public static int NUM_DOCS = 1250000;

  public static void main(String[] args) throws Exception {
    // [1] Read CSV file and parse data set
    log.info("Parsing CSV file ...");
    List<String> texts = new ArrayList<String>();
    long parseStartTime = System.currentTimeMillis();
    parseCSVFile("/home/ishan/code/lucene-cuvs/wikipedia_vector_dump.csv.gz", NUM_DOCS, texts);
    System.out.println("Time taken for parsing dataset: " + (System.currentTimeMillis() - parseStartTime + " ms"));

    // [2] Benchmarking setup

    IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
    config.setMaxBufferedDocs(Integer.MAX_VALUE);
    config.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);

    config.setSimilarity(new BM25Similarity());

    IndexWriter writer = new IndexWriter(new NIOFSDirectory(Path.of("wikipedia.lucene")), config);
      Codec codec = writer.getConfig().getCodec();
      log.info("----------\nIndexing documents using {} ...", codec.getClass().getCanonicalName());
      long indexStartTime = System.currentTimeMillis();
      indexDocuments(NUM_DOCS, writer, texts, COMMIT_FREQ);
      log.info("Time taken for index building (end to end): " + (System.currentTimeMillis() - indexStartTime) + " ms");
  }

  private static void parseCSVFile(String datasetFile, int numDocs, List<String> texts)
      throws IOException, CsvValidationException {
    InputStreamReader isr = null;
    ZipFile zipFile = null;
    if (datasetFile.endsWith(".zip")) {
      zipFile = new ZipFile(datasetFile);
      isr = new InputStreamReader(zipFile.getInputStream(zipFile.entries().nextElement()));
    } else if (datasetFile.endsWith(".gz")) {
      isr = new InputStreamReader(new GZIPInputStream(new FileInputStream(datasetFile)));
    } else {
      isr = new InputStreamReader(new FileInputStream(datasetFile));
    }

    try (CSVReader csvReader = new CSVReader(isr)) {
      String[] csvLine;
      int countOfDocuments = 0;
      while ((csvLine = csvReader.readNext()) != null) {
        if ((countOfDocuments++) == 0)
          continue; // skip the first line of the file, it is a header
        try {
          texts.add(csvLine[2]);
        } catch (Exception e) {
          System.out.print("#");
          countOfDocuments -= 1;
        }
        if (countOfDocuments % 1000 == 0)
          System.out.print(".");

        if (countOfDocuments == numDocs + 1)
          break;
      }
      System.out.println();
    }
    if (zipFile != null)
      zipFile.close();
  }

  private static class NoPositionsTextField extends Field {

	  public static final FieldType TYPE_NOT_STORED = new FieldType();

	  static {
	    TYPE_NOT_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
	    TYPE_NOT_STORED.setTokenized(true);
	    TYPE_NOT_STORED.freeze();
	  }

	  public NoPositionsTextField(String name, String value) {
	    super(name, value, TYPE_NOT_STORED);
	  }
	}

  private static void indexDocuments(int numDocs, IndexWriter writer, List<String> titles,
		  int commitFrequency) throws IOException, InterruptedException {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    AtomicInteger docsIndexed = new AtomicInteger(0);
    AtomicBoolean commitBeingCalled = new AtomicBoolean(false);

    for (int i = 0; i < numDocs - 1; i++) {
      final int index = i;
      pool.submit(() -> {
        Document document = new Document();
        document.add(new StringField("id", String.valueOf(index), Field.Store.YES));
        //document.add(new StringField("body", titles.get(index), Field.Store.YES));
        document.add(new NoPositionsTextField("body", titles.get(index)));
        try {
          while (commitBeingCalled.get())
            ; // block until commit is over
          writer.addDocument(document);
          int docs = docsIndexed.incrementAndGet();
          // if (docs % 100 == 0) log.info("Docs added: " + docs);

          synchronized (pool) {

            if (docs % commitFrequency == 0 && !commitBeingCalled.get()) {
              log.info(docs + " Docs indexed. Commit called...");
              if (commitBeingCalled.get() == false) {
                try {
                  commitBeingCalled.set(true);
                  writer.commit();
                  commitBeingCalled.set(false);
                } catch (IOException ex) {
                  ex.printStackTrace();
                }
              }
              log.info(docs + ": Done commit!");
            }
          }

        } catch (IOException ex) {
          ex.printStackTrace();
        }
      });

    }
    pool.shutdown();
    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

    writer.commit();
    writer.close();
  }


}

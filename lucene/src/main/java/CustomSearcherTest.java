

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

public class CustomSearcherTest {
	public static void main(String[] args) throws IOException {
		Directory directory = new RAMDirectory();
		IndexWriterConfig iwConfig = new IndexWriterConfig();
		IndexWriter indexWriter = new IndexWriter(directory, iwConfig);

		for (int i=0; i<100; i++) {
			Document doc = new Document();
			doc.add(new TextField("title", "my first book is on ishan chattopadhyaya", Store.YES));
			indexWriter.addDocument(doc);
			indexWriter.commit();
		}
		indexWriter.close();

		IndexReader indexReader = DirectoryReader.open(directory);
		AcceleratedSearcher searcher = new AcceleratedSearcher(indexReader, "title");

		Query query = new TermQuery(new Term("title", "book"));
		System.out.println(searcher.search(query, 10).totalHits);
		indexReader.close();
	}
}

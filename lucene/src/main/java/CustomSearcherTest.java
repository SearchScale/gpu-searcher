

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;

import java.util.Arrays;

public class CustomSearcherTest {
	public static void main(String[] args) throws IOException {
		Directory directory = new RAMDirectory();
		IndexWriterConfig iwConfig = new IndexWriterConfig();
		IndexWriter indexWriter = new IndexWriter(directory, iwConfig);

		String wikiFilePath = "enwiki-latest-abstract.xml";
		/*for (int i=0; i<100; i++) {
			Document doc = new Document();
			doc.add(new TextField("title", "my first book is on ishan chattopadhyaya", Store.YES));
			indexWriter.addDocument(doc);
			indexWriter.commit();
		}*/

		String line;
		String title = null, url = null, desc = null;
		BufferedReader br = new BufferedReader(new FileReader(wikiFilePath));
		int counter = 0, totalCounter = 0;
		while((line=br.readLine()) != null) {
		  totalCounter++;
		  if (line.startsWith("<title>")) {
			title = getTitle(line);
		  } else if (line.startsWith("<title>")) {
			url = getUrl(line);
		  } else if (line.startsWith("<abstract>")) {
	
			desc = getDesc(line);
			if (desc.length()>15) {
			  if (counter%10000==0) {
				System.out.println(counter+": "+getTitle(title)+", -->"+desc);
			  }
			  if (counter % 20000 == 0) {
				indexWriter.commit();
			  }
			  Document doc = new Document();
			  doc.add(new TextField("title", title, Field.Store.NO));
			  doc.add(new TextField("desc", desc, Field.Store.NO));
			  indexWriter.addDocument(doc);
	
			  if (counter>=8000000) break;
			  counter++;
			}
		  }
		}
		indexWriter.commit();
		indexWriter.close();

		IndexReader indexReader = DirectoryReader.open(directory);
		AcceleratedSearcher searcher = new AcceleratedSearcher(indexReader, "desc", null);
		searcher.setSimilarity(new BM25Similarity());

		//Query query = new TermQuery(new Term("desc", "societies"));
		BooleanQuery query = new BooleanQuery.Builder()
                                .add(new BooleanClause(new TermQuery(new Term("desc", "born")), Occur.SHOULD))
                                .add(new BooleanClause(new TermQuery(new Term("desc", "often")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("desc", "world")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("desc", "politics")), Occur.SHOULD))
				.build();

		int k = 10;
		TopDocs results = searcher.search(query, k);
	    System.out.println("Hits: "+results.totalHits);
		//System.out.println(Arrays.toString(results.scoreDocs));
		for (int i=0; i<k; i++) {
			System.out.println(results.scoreDocs[i]);
		}

		indexReader.close();
		System.out.println("Test finished...");
	}

	public static String getTitle(String str) {
		return str.replaceAll("<title>", "").replaceAll("</title>", "").replaceAll("Wikipedia: ", "");
	  }
	
	  public static String getUrl(String str) {
		return str.substring("<url>".length()).replaceAll("</url>", "");
	  }
	
	  public static String getDesc(String str) {
		return str.substring("<abstract>".length()).replaceAll("</abstract>", "");
	  }
	
}

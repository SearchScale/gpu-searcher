import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class Gov2Test {
	public static void main(String[] args) throws IOException {
		Directory directory = FSDirectory.open(Paths.get("/home/ishan/code/gov2.lucene/index"));
		IndexReader indexReader = DirectoryReader.open(directory);
		
		Set<String> whitelist = new HashSet<>();
//		whitelist.add("born");
		whitelist.add("html");
		whitelist.add("hello");
		whitelist.add("world");
		whitelist.add("government");
		whitelist.add("z’berg");
		
		AcceleratedSearcher searcher = new AcceleratedSearcher(indexReader, "body", whitelist);
		searcher.setSimilarity(new BM25Similarity());

		//Query query = new TermQuery(new Term("desc", "societies"));
		BooleanQuery query = new BooleanQuery.Builder()
/*				.add(new BooleanClause(new TermQuery(new Term("desc", "born")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("desc", "often")), Occur.SHOULD))*/
				.add(new BooleanClause(new TermQuery(new Term("body", "world")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "government")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "hello")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "html")), Occur.SHOULD))
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
}

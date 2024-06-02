import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
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

public class WikipediaTest {
	public static void main(String[] args) throws IOException {
		Directory directory = FSDirectory.open(Paths.get(args[0]));
		IndexReader indexReader = DirectoryReader.open(directory);

		AcceleratedSearcher searcher = new AcceleratedSearcher(indexReader, "body", Collections.emptySet());
		searcher.setSimilarity(new BM25Similarity());

		BooleanQuery query = new BooleanQuery.Builder()
				.add(new BooleanClause(new TermQuery(new Term("body", "world")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "government")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "hello")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "new")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "last")), Occur.SHOULD))
				.build();

		int k = 10;
		TopDocs results = searcher.search(query, k);
		System.out.println("Hits: "+results.totalHits);
		for (int i=0; i<k; i++) {
			System.out.println(results.scoreDocs[i]);
		}

		indexReader.close();
		System.out.println("Test finished...");
	}
}

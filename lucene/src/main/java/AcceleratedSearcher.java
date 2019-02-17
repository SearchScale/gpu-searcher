

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

public class AcceleratedSearcher extends IndexSearcher {

	private CudaIndex gpuSearcher;
	public AcceleratedSearcher(IndexReader r, String field, Set<String> whitelist) {
		super(r);
		try {
			gpuSearcher = new CudaIndex(r, this, leafContexts, field, whitelist);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	String[] getQueryTerms(Query query) {
		if (query instanceof BooleanQuery) {
			BooleanQuery bq = (BooleanQuery) query;
			String terms[] = new String[bq.clauses().size()];

			for (int i=0; i<bq.clauses().size(); i++) {
				BooleanClause clause = bq.clauses().get(i);
				if (clause.getOccur().equals(Occur.SHOULD) != true) {
					return null;
				}
				terms[i] = ((TermQuery)clause.getQuery()).getTerm().text();
			}
			return terms;
		}
		return null;
	}
	@Override
	public TopDocs search(Query query, int n) throws IOException {
		System.out.println("Specialized searcher was called... Term dictionary size: "+gpuSearcher.termDictionary.size());
		
		long start = System.nanoTime();

		String queryTerms[] = getQueryTerms(query);
		int presentTerms = 0;
		for (String term: queryTerms) {
			if (gpuSearcher.termDictionary.get(term) != null) {
				presentTerms++;
			}
		}
		//System.out.println(">>> Present terms: "+presentTerms);
		//System.out.println(">>> Query terms: "+Arrays.toString(queryTerms));
		//System.out.println(">>> Dict terms: "+gpuSearcher.termDictionary.keySet());

		int terms[] = new int[presentTerms];
		for (int i=0, t=0; i<queryTerms.length; i++) {
			if (gpuSearcher.termDictionary.get(queryTerms[i]) != null) {
				terms[t++] = gpuSearcher.termDictionary.get(queryTerms[i]);
			}
		}
		System.out.println("Query terms: "+Arrays.toString(terms));
		long lap = System.nanoTime();
		//System.out.println("Query terms: "+Arrays.toString(terms));
		TopDocs topDocsGpu = gpuSearcher.search(terms);
		long end = System.nanoTime();
		System.out.println("Cuda searcher took: "+(end-start)/1000000.0);
		System.out.println("Term resolution time: "+(lap-start)/1000000.0);
		
		start = System.nanoTime();
		TopDocs topDocsCpu = super.search(query, n);
		end = System.nanoTime();
		System.out.println("Lucene searcher took: "+(end-start)/1000000.0);
		return topDocsCpu;
	}
}

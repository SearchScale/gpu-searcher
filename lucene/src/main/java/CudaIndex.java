

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

public class CudaIndex {
	
	private CudaIndexJni jni = new CudaIndexJni();
	protected ConcurrentHashMap<String, Integer> termDictionary = new ConcurrentHashMap<>();
	private Set<String> whitelist = null;
	
	public CudaIndex(IndexReader r, IndexSearcher s, List<LeafReaderContext> leaves, String field, 
			Set<String> whitelist) throws IOException {
		this.whitelist = whitelist;
		BM25Similarity sim = (BM25Similarity) s.getSimilarity(true);
		CollectionStatistics collStats = null;
		try {
			collStats = s.collectionStatistics(field);
		} catch (IOException e) {
			e.printStackTrace();
		}
		float avgdl = (float) (collStats.sumTotalTermFreq() / (double) collStats.docCount());
		float b = sim.getB();
		float k1 = sim.getK1();

		System.out.println(avgdl);
		System.out.println(b);
		System.out.println(k1);

		Set<String> allTerms = new LinkedHashSet<>();
		Map<String, List<DocFreq>> globalPostings = new TreeMap<String,List<DocFreq>>();
		TreeMap<Integer, Float> normsMap = new TreeMap();

		int baseOffset = 0;
		int TOTAL_DOCS = 0;
		for (int i=0; i<leaves.size(); i++) {
			System.out.println("Leaf: "+i);
			System.out.println("Docs: "+leaves.get(i).reader().maxDoc());
			TermsEnum myTerms = leaves.get(i).reader().terms(field).iterator();

			BytesRef t;
			while ((t = myTerms.next() )!= null) {

				if (whitelist != null && whitelist.contains(t.utf8ToString()) == false) {
					continue;
				}
				// get idf
				//TermStates state = TermStates.build(s.getIndexReader().getContext(), new Term("desc", t), true);
				Term term = new Term(field, t);
				TermContext termContext = TermContext.build(s.getTopReaderContext(), term);
				TermStatistics termStats = s.termStatistics(term, termContext);
				float idf = sim.idfExplain(collStats, termStats).getValue();
				//System.out.println(" ?? "+t.utf8ToString()+", idf: "+idf);

				allTerms.add(t.utf8ToString());
				//System.out.println("Term: "+t.utf8ToString());
				PostingsEnum postings = myTerms.postings(null, PostingsEnum.FREQS);
				//System.out.println("Postings: "+postings);
				postings.nextDoc();
				int docId;
				while ((docId = postings.docID()) != 2147483647) {
					//System.out.print(docId + "("+postings.freq()+"), ");
					List<DocFreq> list = globalPostings.containsKey(t.utf8ToString())? globalPostings.get(t.utf8ToString()): new ArrayList<>();
					list.add(new DocFreq(baseOffset+docId, postings.freq(), idf));
					globalPostings.put(t.utf8ToString(), list);
					postings.nextDoc();
				}
				//System.out.println();
			}

			// prepare cache
			float[] LENGTH_TABLE = new float[256];
			for (int j = 0; j < 256; j++) {
				LENGTH_TABLE[j] = SmallFloat.byte4ToInt((byte) j);
			}
			final float[] cache = new float[256];
			for (int j = 0; j < cache.length; j++) {
				cache[j] = k1 * ((1 - b) + b * LENGTH_TABLE[j] / avgdl);
			}

			// get norms
			List<Integer> normValues = new ArrayList<>();
			NumericDocValues norms = leaves.get(i).reader().getNormValues(field);
			norms.nextDoc();
			long d = -1, n = -1;

			// write norm values
			for (int j=0; (d = norms.docID()) != 2147483647; j++) {
				n = norms.longValue();
				//System.out.println("Doc: "+(baseOffset+d)+", norm: "+cache[(int)n & 0xFF]);
				normsMap.put(baseOffset+(int)d, cache[(int)n & 0xFF]);
				norms.nextDoc();
			}

			baseOffset += leaves.get(i).reader().maxDoc();
			TOTAL_DOCS += leaves.get(i).reader().maxDoc();
		}

		System.out.println("Total Docs: "+TOTAL_DOCS);
		/*

		// writing it out
	    for (int key: normsMap.keySet()) {
	        System.out.print(key+" "+normsMap.get(key)+"\n");
	      }

	      System.out.println("Number of terms: "+allTerms.size());
	      for (Object key: globalPostings.keySet()) {
	        //System.out.println(key+": "+globalPostings.get(key));
	    	  System.out.print(key+"\n");
	        for (int j=0; j<globalPostings.get(key).size(); j++) {
	          DocFreq freq = globalPostings.get(key).get(j);
	          System.out.print(freq.docId+" "+freq.freq+" "+freq.idf+" ");
	        }
	        System.out.print("\n");
	      }
	      System.out.println("Number of terms: "+allTerms.size());
		 */

		List<Integer> startPositions = new ArrayList<>();
		List<Integer> docIds = new ArrayList<>();
		List<Float> partialScores = new ArrayList<>();

		int counter = 0;
		FileWriter termDict = new FileWriter("term-dict.txt"); 
		for (Object key: globalPostings.keySet()) {
	        int pos = docIds.size();
	        startPositions.add(pos);

			termDict.write(key+"\n");
			termDictionary.put(key.toString(), (counter++));

			for (int j=0; j<globalPostings.get(key).size(); j++) {
				DocFreq freq = globalPostings.get(key).get(j);
				//System.out.print(freq.docId+" "+freq.freq+" "+freq.idf+" ");
				docIds.add(freq.docId);
				/*if (freq.docId==0) {
					System.out.println(" %%%%% "+key);
					System.out.println(" %%%%% "+freq.idf);
					System.out.println(" %%%%% "+k1);
					System.out.println(" %%%%% "+freq.freq);
					System.out.println(" %%%%% "+normsMap.get(freq.docId));
					System.out.println(" %%%%% ");

				}*/
				partialScores.add(freq.idf * (k1+1) * (float)freq.freq / ((float)freq.freq + normsMap.get(freq.docId)));
			}
		}
		termDict.close();

		/*System.out.println(startPositions);
		System.out.println(docIds);
		System.out.println(partialScores);*/

		System.out.println("Java: terms "+startPositions.size()+", postings: "+partialScores.size());

		long start = System.nanoTime();
		jni.initIndex(toArrayInt(docIds), toArrayFloat(partialScores), toArrayInt(startPositions), (long)TOTAL_DOCS);
		long end = System.nanoTime();
		System.out.println("Cuda searcher initialization took (ms): "+(end-start)/1000000.0);

		/*int terms[] = {4055, 5071};
		long start = System.nanoTime();
		search(terms);
		long end = System.nanoTime();
		System.out.println("Cuda searcher took: "+(end-start)/1000000.0);*/
	}
	
	public TopDocs search(int terms[], int n) {
		Object results = jni.getScores(terms, n);

		ByteBuffer buf = ((ByteBuffer)results).order(ByteOrder.nativeOrder());
		int N = buf.limit() / 8;
		System.out.println("Java received array elements: "+results);
		ScoreDoc scoreDocs[] = new ScoreDoc[N];
		float maxScore = Float.NEGATIVE_INFINITY;
		for (int i=0; i<N; i++) {
			int id = buf.getInt((i)*4);
			float score = buf.getFloat((N+i)*4);
			/*if (i<10) {
				System.out.println("DocId: " + id + ", Distance: "
						+ score);
			}*/
			scoreDocs[i] = new ScoreDoc(id, score);
			maxScore = Math.max(maxScore, score);
		}
		return new TopDocs(N, scoreDocs, maxScore);
	}

	int[] toArrayInt(List<Integer> list) {
		int ret[] = new int[list.size()];
		for (int i=0; i<list.size(); i++)
			ret[i] = list.get(i);
		return ret;
	}
	float[] toArrayFloat(List<Float> list) {
		float ret[] = new float[list.size()];
		for (int i=0; i<list.size(); i++)
			ret[i] = list.get(i);
		return ret;
	}

	private class DocFreq {
		int docId, freq;
		float idf;
		public DocFreq(int docId, int freq, float idf) {
			this.docId = docId;
			this.freq = freq;
			this.idf = idf;
		}

		@Override
		public String toString() {
			return docId + " ("+freq+", idf="+idf+")";
		}
	}
}

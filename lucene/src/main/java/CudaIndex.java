

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
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
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public class CudaIndex {

	private CudaIndexJni jni = new CudaIndexJni();
	protected ConcurrentHashMap<String, Integer> termDictionary = new ConcurrentHashMap<>();
	private Set<String> whitelist = null;

	public CudaIndex(IndexReader r, IndexSearcher s, List<LeafReaderContext> leaves, String field, 
			Set<String> whitelist) throws IOException, ClassNotFoundException {
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
		TreeMap<Integer, Float> normsMap = new TreeMap();

		//Map<String, List<DocFreq>> globalPostings = new TreeMap<String,List<DocFreq>>();
		//Map<String, List<Integer>> globalPartialScoresDocs = new TreeMap();
		//Map<String, List<Float>> globalPartialScoresScore = new TreeMap();

		int docIdsArray[]; 
		float scoresArray[];
		int startPositionsArray[]; 
		int TOTAL_DOCS = 0;

		long start = System.nanoTime();

		if (new File("docids.arr").exists() == false) {

			//DB db = DBMaker.memoryDB().make();
			DB db = DBMaker.fileDB("file.db").fileMmapEnable().concurrencyDisable().make();
			//ConcurrentMap map = db.hashMap("map").createOrOpen();
			//map.put("something", "here");

			Map globalPartialScoresDocs = db.hashMap("ids").create();
			Map globalPartialScoresScore = db.hashMap("scores").create();

			int baseOffset = 0;
			for (int i=0; i<leaves.size(); i++) {
				System.out.println("Leaf: "+i);
				System.out.println("Docs: "+leaves.get(i).reader().maxDoc());
				TermsEnum myTerms = leaves.get(i).reader().terms(field).iterator();

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

				BytesRef t;
				int counter = 0;
				long totalTillNow = 0;
				while ((t = myTerms.next() )!= null) {
					counter++;
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

					int postingsSize = getSize(myTerms);
					List<Integer> docList = globalPartialScoresDocs.containsKey(t.utf8ToString())? (List<Integer>)globalPartialScoresDocs.get(t.utf8ToString()): new ArrayList<>(postingsSize);
					List<Float> scoreList = globalPartialScoresScore.containsKey(t.utf8ToString())? (List<Float>)globalPartialScoresScore.get(t.utf8ToString()): new ArrayList<>(postingsSize);
					while ((docId = postings.docID()) != 2147483647) {
						docList.add(baseOffset+docId);
						scoreList.add((float)(idf * (k1+1) * (double)postings.freq() / ((double)postings.freq() + normsMap.get(baseOffset + docId))));
						postings.nextDoc();
					}
					globalPartialScoresDocs.put(t.utf8ToString(), docList);
					globalPartialScoresScore.put(t.utf8ToString(), scoreList);

					totalTillNow += docList.size();
					System.out.println(counter+": "+term.text()+": postings length is size: "+postingsSize+", total: "+totalTillNow);
				}
				baseOffset += leaves.get(i).reader().maxDoc();
				TOTAL_DOCS += leaves.get(i).reader().maxDoc();
			}

			System.out.println("Total Docs: "+TOTAL_DOCS);


			List<Integer> startPositions = new ArrayList<>();
			List<Integer> docIds = new ArrayList<>();
			List<Float> partialScores = new ArrayList<>();

			int counter = 0;
			FileWriter termDict = new FileWriter("term-dict.txt"); 
			for (Object key: globalPartialScoresDocs.keySet()) {
				int pos = docIds.size();
				startPositions.add(pos);

				termDict.write(key+"\n");
				termDictionary.put(key.toString(), (counter++));

				System.out.println("Finalizing term: "+key);
				List<Integer> docList = ((List)globalPartialScoresDocs.get(key));
				List<Float> scoreList = ((List)globalPartialScoresScore.get(key));

				for (int j=0; j<docList.size(); j++) {
					docIds.add(docList.get(j));
					partialScores.add(scoreList.get(j));
				}
			}
			termDict.close();

			System.out.println("Java: terms "+startPositions.size()+", postings: "+partialScores.size());

			docIdsArray = toArrayInt(docIds); 
			scoresArray = toArrayFloat(partialScores);
			startPositionsArray = toArrayInt(startPositions); 

			writeToFile(docIdsArray, "docids.arr");
			writeToFile(scoresArray, "scores.arr");
			writeToFile(startPositions, "starts.arr");
			writeToFile(TOTAL_DOCS, "totaldocs.int");
			
			db.close();
		} else {
			docIdsArray = (int[]) readFromFile("docids.arr");
			scoresArray = (float[]) readFromFile("scores.arr");
			startPositionsArray = toArrayInt((ArrayList<Integer>) readFromFile("starts.arr"));
			TOTAL_DOCS = (int)readFromFile("totaldocs.int");
			String line;
			try (BufferedReader in = new BufferedReader(new FileReader("term-dict.txt"))) {
				int counter = 0;
				while((line=in.readLine())!=null) {
					termDictionary.put(line, counter++);
				}
			}			
		}

		for (int i=0; i<20; i++) {
			System.out.print(startPositionsArray[i] + "$"+docIdsArray[i]+": "+scoresArray[i]+", ");
		}
		System.out.println();
		jni.initIndex(docIdsArray, scoresArray, startPositionsArray, (long)TOTAL_DOCS);
		long end = System.nanoTime();
		System.out.println("Cuda searcher initialization took (ms): "+(end-start)/1000000.0);

		/*int terms[] = {4055, 5071};
		long start = System.nanoTime();
		search(terms);
		long end = System.nanoTime();
		System.out.println("Cuda searcher took: "+(end-start)/1000000.0);*/

		
	}

	void writeToFile(Object obj, String filename) throws IOException {
		FileOutputStream f = new FileOutputStream(new File(filename));
		ObjectOutputStream o = new ObjectOutputStream(f);

		// Write objects to file
		o.writeObject(obj);

		o.close();
		f.close();
	}

	Object readFromFile(String filename) throws IOException, ClassNotFoundException {
		FileInputStream fi = new FileInputStream(new File(filename));
		ObjectInputStream oi = new ObjectInputStream(fi);

		Object obj = oi.readObject();

		oi.close();
		fi.close();
		return obj;
	}

	int getSize(TermsEnum myTerms) throws IOException {
		PostingsEnum postings = myTerms.postings(null, PostingsEnum.FREQS);
		postings.nextDoc();

		int counter =0;

		int docId;
		while ((docId = postings.docID()) != 2147483647) {
			counter++;
			postings.nextDoc();
		}
		return counter;
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

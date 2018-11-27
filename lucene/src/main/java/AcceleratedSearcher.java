

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;

public class AcceleratedSearcher extends IndexSearcher {

	private CudaIndex gpuSearcher;
	public AcceleratedSearcher(IndexReader r, String field) {
		super(r);
		try {
			gpuSearcher = new CudaIndex(r, this, leafContexts, field);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
}

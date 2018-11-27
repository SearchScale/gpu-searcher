import com.github.projects.utils.JavaUtils;

public class CudaIndexJni {
    static {
        JavaUtils.loadLibrary("cuda-searcher.so");
    }

    
	public native int initIndex(int[] docIds, float[] partialScores, int[] startOffsets);
	public native Object getScores(int terms[]);
}


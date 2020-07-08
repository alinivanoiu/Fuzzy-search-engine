package disertatie.server.utils;

import com.mathworks.engine.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MatlabUtils {
    private static MatlabEngine eng = null;
    public MatlabUtils() {
                try{
                    this.initializeMatlab();
                }
                catch(Exception e){
                    System.out.println(e);
                }
        
    }
public void initializeMatlab()
        throws IllegalArgumentException, IllegalStateException, InterruptedException, MatlabExecutionException,
        MatlabSyntaxException, ExecutionException {
    eng = MatlabEngine.startMatlab(); 
    final String path = "C:\\Users\\Alin\\Desktop\\SE.fis";
    eng.eval("a=readfis('" + path + "')");
}
 public List<Double> calculateFuzzyRelevance(final List<Double> inputs1, final List<Double> inputs2)
         throws InterruptedException, ExecutionException {
     final List<Double> relevance = new ArrayList<>();

     for (int i = 0; i < inputs1.size(); i++) {
         final double crtInput1 = inputs1.get(i);
         final double crtInput2 = inputs2.get(i);
         eng.eval("output=evalfis([" + crtInput1 + " " + crtInput2 + "],a)");
         final double res = eng.getVariable("output");
         relevance.add(res);
     }
  
     return relevance;

 }
}

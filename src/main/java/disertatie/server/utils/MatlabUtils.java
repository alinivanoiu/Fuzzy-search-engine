package disertatie.server.utils;

import com.mathworks.engine.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MatlabUtils {
    private static MatlabEngine eng = null;
    // se declară un constructor explicit cu un bloc try - catch
    public MatlabUtils() {
                try{
                    this.initializeMatlab();
                }
                catch(Exception e){
                    System.out.println(e);
                }
        
    }
// se inițializează Matlab
public void initializeMatlab()
        throws IllegalArgumentException, IllegalStateException, InterruptedException, MatlabExecutionException,
        MatlabSyntaxException, ExecutionException {
    // se pornește o instanță a Matlab-ului
    eng = MatlabEngine.startMatlab(); 
    // se setează calea către fișierul .FIS
    final String path = "C:\\Users\\Alin\\Desktop\\SE.fis";
    // se citește acel .FIS
    eng.eval("a=readfis('" + path + "')");
}
 // funcție pentru calculul relevanței rezultatelor
 public List<Double> calculateFuzzyRelevance(final List<Double> inputs1, final List<Double> inputs2)
         throws InterruptedException, ExecutionException {
     // listă pentru stocarea procentelor de relevanță pentru fiecare rezultat
     final List<Double> relevance = new ArrayList<>();
     // se parcurg edit distance si division pentru fiecare rezultat
     for (int i = 0; i < inputs1.size(); i++) {
         final double crtInput1 = inputs1.get(i);
         final double crtInput2 = inputs2.get(i);
         // se transferă către Matlab pentru evaluare
         eng.eval("output=evalfis([" + crtInput1 + " " + crtInput2 + "],a)");
         // se citește variabila output
         final double res = eng.getVariable("output");
         // se adaugă în listă
         relevance.add(res);
     }
     return relevance;

 }
}

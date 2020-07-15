package disertatie.server.controllers;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.customsearch.Customsearch;
import com.google.api.services.customsearch.model.Result;
import com.google.api.services.customsearch.model.Search;
import disertatie.server.domain.vo.DuckDuckLink;
import disertatie.server.domain.vo.GoogleSearchLink;
import disertatie.server.utils.Levenshtein;
import disertatie.server.utils.MatlabUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

// permite primirea request-urilor din exteriorul aplicației
@CrossOrigin("*")
// permite returnarea rezultatelor în format JSON/XML
@RestController
// setează BASE URL-ul la care să răspundă acest controller
@RequestMapping("/")
public class SearchRestController {
    // timpul maxim pe care să-l aștepte când se va face o căutare la Google
    private int HTTP_REQUEST_TIMEOUT = 3 * 600000;
    // variabile ce vor fi trimise o dată cu request-ul pentru căutarea pe Google
    private String GOOGLE_API_KEY = "AIzaSyCSI36qVUKUfDaV2DjSUSUDpdzrIlGxIfo";
    private String SEARCH_ENGINE_ID = "015970870679697835201:iskqgfupniq";
    // se creează o nouă instanță de Matlab și se inițializează workspace-ului prin citirea fișierului SE.fis
    MatlabUtils matlabEngine = new MatlabUtils();

    // se creează endpoint-ul la care va trimite aplicația client cuvinte cheie pentru a efectua căutări
    @GetMapping(path = "/search/{keywords}")
    public HashMap<String,Object> search(@PathVariable String keywords) throws ExecutionException, InterruptedException {
        // se despart cuvintele cheie in atomi lexicali pentru a putea aplica Levenshtein ulterior
        String queryWords[] = keywords.split(" ");
        // se inițializează lista ce va conține rezultatele finale
        List<GoogleSearchLink> googleResults = new ArrayList<>();
        List<DuckDuckLink> duckduckResults = new ArrayList<>();
        // se va efectua căutarea și calcularea relevanței, iar valoarile vor fi stocate în googleResults
        processGooglePrecission(keywords, queryWords, googleResults);
         // se va efectua căutarea și calcularea relevanței, iar valoarile vor fi stocate în duckduckResults
        processDuckDuckGoPrecission(keywords, queryWords, duckduckResults);
        // HashMap ce va conține obiectul cu rezultatele ambelor căutări și relevanțele asociate
        HashMap<String, Object> fuzzyResults = new HashMap<String, Object>();
        fuzzyResults.put("googleResults",googleResults);
        fuzzyResults.put("duckResults",duckduckResults);
        return fuzzyResults;
    }
    // funcție pentru căutarea pe duckduckgo și calcularea levenshtein pentru textul fiecărui website
    private void processDuckDuckGoPrecission(String keywords, String[] queryWords, List<DuckDuckLink> duckduckResults)
            throws InterruptedException, ExecutionException {
        List<Integer> distances = new ArrayList<>();
        // listă pentru stocarea distanței Levenshtein
        List<Double> inputs1 = new ArrayList<>();
         // listă pentru stocarea procentului de cuvinte găsite
        List<Double> inputs2 = new ArrayList<>();
        // liste pentru stocarea datelor despre paginile web
        List<String> filteredTexts= new ArrayList<>();
        List<String> filteredURLS = new ArrayList<>();
        // listă pentru stocarea atomilor lexicali ai fiecărui Text
        List<String[]> items = new ArrayList<>();

        searchOnDuckDuckGo(filteredTexts, filteredURLS, keywords, queryWords);
        // se adaugă fiecare Text separat în atomi lexicali
        for(String it: filteredTexts){
            items.add(it.split(" "));
        }
        // se calculează editDistance și division pentru atomii lexicali
        calculateAllInputs(items, queryWords, distances, inputs1, inputs2);
        // se recalculeaza input2 sub formă procentuală
        recalculateInput2(inputs2, keywords.length());
        // se calculează relevanța pentru fiecare pagină rezultată
        List<Double> relevance = matlabEngine.calculateFuzzyRelevance(inputs1,inputs2);
        // se stochează textul, url-ul și relevanța în lista duckduckResults primită ca argument
        // acest lucru va face lista disponbilă în exteriorul acestei funcții
        for(int i = 0; i < filteredTexts.size();i++){
            String currentText = filteredTexts.get(i);
            String currentURL = filteredURLS.get(i);
            Double currentRelevance = relevance.get(i);
            duckduckResults.add(new DuckDuckLink(currentText,currentURL,currentRelevance));
        }
    }
    // funcție pentru căutarea pe google și calcularea levenshtein pentru titlu și pentru summary-ul fiecărui website
    private void processGooglePrecission(String keywords, String[] queryWords, List<GoogleSearchLink> googleResults)
            throws InterruptedException, ExecutionException {
        // listă pentru stocarea rezultatelor
        List<Result> chromeSearchResults;
        // listă pentru stocarea titlurilor paginilor web
        List<String[]> chromeResTitles = new ArrayList<>();
        // listă pentru stocarea summary-urilor paginilor web
        List<String[]> chromeResSnippets = new ArrayList<>();
        // listă pentru stocarea distanței calculate pentru titluri
        List<Integer> distancesForTitles = new ArrayList<>();
        // listă pentru stocarea distanței calculate pentru summary
        List<Integer> distancesForSnippets = new ArrayList<>();
        // listă pentru stocarea edit distance-ului pentru titluri
        List<Double> inputs1ForTitles = new ArrayList<>();
        // listă pentru stocarea edit distance-ului pentru summary
        List<Double> inputs1ForSnippets = new ArrayList<>();
        // listă pentru stocarea procentului de cuvinte regăsite în titluri din cuvintele cheie 
        List<Double> inputs2ForTitles = new ArrayList<>();
        // listă pentru stocarea procentului de cuvinte regăsite în summary-uri din cuvintele cheie 
        List<Double> inputs2ForSnippets = new ArrayList<>();
        // listele folosite de către Matlab pentru calcularea relevanței, după ce deja au fost însumate valorile
        List<Integer> finalDistances = new ArrayList<>();
        List<Double> finalInputs1 = new ArrayList<>();
        List<Double> finalInputs2 = new ArrayList<>();
        // se efectuează căutarea propriu-zisă pe google
        chromeSearchResults = searchOnGoogle(keywords);
        // se parcurg rezultatele și se extrag datele necesare
        for (Result res : chromeSearchResults) {
            String title = res.getTitle();
            String snippet = res.getSnippet();
            String url = res.getFormattedUrl();
            // se populează lista finală cu obiecte GoogleSearchLink, iar după primirea listei cu relevanța fiecărui link se va seta proprietatea
            // fuzzyRelevance pentru fiecare obiect
            googleResults.add(new GoogleSearchLink(title,snippet,url,0.0));
            chromeResTitles.add(title.split(" "));
            chromeResSnippets.add(snippet.split(" "));
        }
        // se calculează inputs1 și inputs2 pentru fiecare titlu ar rezultatelor
        calculateAllInputs(chromeResTitles, queryWords, distancesForTitles, inputs1ForTitles, inputs2ForTitles);
        // se calculează inputs1 și inputs2 pentru fiecare summary ar rezultatelor
        calculateAllInputs(chromeResSnippets, queryWords, distancesForSnippets, inputs1ForSnippets, inputs2ForSnippets);
        // se adună inputs1 și inputs2
        addTheValuesFromTitleAndSnippets(distancesForTitles, inputs1ForTitles, inputs2ForTitles, distancesForSnippets,
                inputs1ForSnippets, inputs2ForSnippets, finalDistances, finalInputs1, finalInputs2);
        // se recalculează inputs2 sub formă de procent, luând în considerare o marjă de eroare de 10%
        recalculateInput2(finalInputs2, keywords.length());
        // se calculează relevanța fuzzy pe baza listelor însumate ale inputs1 și inputs2
        List<Double> relevance = (ArrayList) matlabEngine.calculateFuzzyRelevance(finalInputs1, finalInputs2);
        // se setează proprietățile obiectelor cu relevanța calculată
        for(int i = 0; i<googleResults.size();i++){
            GoogleSearchLink crtLink = googleResults.get(i);
            Double crtRelevance = relevance.get(i);
            crtLink.setFuzzyRelevance(crtRelevance);
        }
    }
    // funcție pentru însumarea input1 de la titlu cu input1 de la snippet, la fel și pentru input2
    private void addTheValuesFromTitleAndSnippets(List<Integer> distancesTitle,
                                                  List<Double> inputs1Title, List<Double> inputs2Title,
                                                  List<Integer> distancesSnippets,
                                                  List<Double> inputs1Snippets, List<Double> inputs2Snippets,
                                                  List<Integer> finalDistances, List<Double> finalInputs1,
                                                  List<Double> finalInputs2
                                                  ) {
        // se adună distanțele
        for(int i = 0; i < distancesTitle.size();i++){
            int dist1 = distancesTitle.get(i);
            int dist2 = distancesSnippets.get(i);
            int finalDist = dist1+dist2;
            finalDistances.add(finalDist);
        }
        // se adună inputs1
        for(int i = 0; i < inputs1Title.size(); i++){
            double input1Title = inputs1Title.get(i);
            double input1Snippet = inputs1Snippets.get(i);
            double finalInput1 = input1Snippet + input1Title;
            finalInputs1.add(finalInput1);
        }
        // se adună inputs2
        for(int i = 0; i < inputs2Title.size(); i++){
            double input2Title = inputs2Title.get(i);
            double input2Snippet = inputs2Snippets.get(i);
            double finalInput2 = input2Snippet + input2Title;
            finalInputs2.add(finalInput2);
        }
    }
    // calculează input1 și input2 pentru fiecare item / rezultat 
    private void calculateAllInputs(List<String[]> items, String queryWords[], List<Integer> distances,
                                    List<Double> inputs1, List<Double> inputs2) {
        for (String item[] : items) {
            for (String query : queryWords) {
                int currentDistance = 0;
                int input2 = 0;
                for (String word : item) {
                    int wordLength = word.length();
                    currentDistance += Levenshtein.distance(query, word);
                    if (((double) currentDistance / (double) wordLength) < 0.22) {
                        input2 += 1;
                    }
                }
                inputs2.add((double)input2);
                inputs1.add(calculateInput1(currentDistance, String.join(" ", item)));
                distances.add(currentDistance);
            }
        }
    }
    // calculează input1 sub formă de procent
    private double calculateInput1(int ed, String content){
        double itemLength = content.length();
        return (100 * (1 - ((double) ed / itemLength)));
    }
    // functie pentru stocarea valorilor input2 recalculate
    private void recalculateInput2(List<Double> finalInput2, int contentLength){
        for(int i = 0; i < finalInput2.size();i++){
            double currentInput2 = finalInput2.get(i);
            finalInput2.set(i, calculateInput2(currentInput2, contentLength));
        }
    }
    // calculează input2 sub formă de procent și adaugă 10% pentru a compensa posibile erori
    private double calculateInput2(double input2, int contentLength){
        double temp = 0;
        double finalInput = input2;
        if(input2 > 0) {
            temp = 100 * finalInput * 0.1;
        }
        finalInput= 100 * (finalInput/ (double)contentLength);
        if(finalInput > 0) {
            finalInput = finalInput + temp;
        }
        if(finalInput > 100) {
            finalInput = 100;
        }
        return finalInput;
    }
    // funcție pentru căutarea cu google
    private List<Result> searchOnGoogle(String keywords) {
        Customsearch customsearch = null;
        try {
            customsearch = new Customsearch(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
                public void initialize(HttpRequest httpRequest) {
                    try {
                        // setează connect și read timeouts
                        httpRequest.setConnectTimeout(HTTP_REQUEST_TIMEOUT);
                        httpRequest.setReadTimeout(HTTP_REQUEST_TIMEOUT);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Result> resultList = null;
        try {
            Customsearch.Cse.List list = customsearch.cse().list(keywords);
            // setează GOOGLE API KEY
            list.setKey(GOOGLE_API_KEY);
            // setează SEARCH_ENGINE_ID
            list.setCx(SEARCH_ENGINE_ID);
            // execută query
            Search results = list.execute();
            // preia rezultatele
            resultList = results.getItems();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }
     // funcție pentru căutarea cu DuckDuckGo
    private void searchOnDuckDuckGo(List<String> filteredTexts, List<String> filteredURLS, String keywords,
                                    String[] queryWords){
        JSONObject json = null;
        JSONArray relatedTopics = null;
        try {
            JSONParser parser = new JSONParser();
            URL url = new URL("http://api.duckduckgo.com/?format=json&pretty=1&t=ClientName&q="+String.join("+",keywords.split(" ")));
            URLConnection conn = url.openConnection();
            // este necesară specificarea User-Agent-ului drept request property și în URL pentru a funcționa
            conn.setRequestProperty("User-Agent", "ClientName");
            Scanner scn = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String buffer = scn.hasNext() ? scn.next() : "";
            // se preiau rezultatele în format JSON
            json = (JSONObject)parser.parse(buffer);
            // parcurgem rezultatele pentru a prelua Text și URL
            relatedTopics = (JSONArray)json.get("RelatedTopics");
            for(int i = 0; i < relatedTopics.size();i++){
                JSONObject obj = (JSONObject) relatedTopics.get(i);
                if(obj.containsKey("Topics")){
                    JSONArray topicsArray = (JSONArray)obj.get("Topics");
                    for(int j = 0; j<topicsArray.size(); j++){
                        JSONObject crtTopic = (JSONObject)topicsArray.get(j);
                        String crtText = (String)crtTopic.get("Text");
                        String crtURL = (String)crtTopic.get("FirstURL");
                        filteredTexts.add(crtText);
                        filteredURLS.add(crtURL);
                    }
                }
                if(obj.containsKey("Text")){
                    String crtText = (String)obj.get("Text");
                    String crtURL = (String)obj.get("FirstURL");
                    filteredTexts.add(crtText);
                    filteredURLS.add(crtURL);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        }
    }


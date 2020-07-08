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

@CrossOrigin("*")
@RestController
@RequestMapping("/")
public class BookRestController {
    private int HTTP_REQUEST_TIMEOUT = 3 * 600000;
    private String GOOGLE_API_KEY = "AIzaSyCSI36qVUKUfDaV2DjSUSUDpdzrIlGxIfo";
    private String SEARCH_ENGINE_ID = "015970870679697835201:iskqgfupniq";
    private final static String DUCKDUCKGO_SEARCH_URL = "https://html.duckduckgo.com/html/?q=";
    // pornitul aplicatiei dureaza mai mult din cauza pornirii matlab-ului,
    // dar asta ofera avantaje pe partea vitezei de procesare a rezultatelor
    MatlabUtils matlabEngine = new MatlabUtils();
   
    @GetMapping(path = "/search/{keywords}")
    public HashMap<String,Object> search(@PathVariable String keywords) throws ExecutionException, InterruptedException {
        String queryWords[] = keywords.split(" ");
        List<GoogleSearchLink> googleResults = new ArrayList<>();
        List<DuckDuckLink> duckduckResults = new ArrayList<>();
        processGooglePrecission(keywords, queryWords, googleResults);
        processDuckDuckGoPrecission(keywords, queryWords, duckduckResults);
        HashMap<String, Object> fuzzyResults = new HashMap<String, Object>();
        fuzzyResults.put("googleResults",googleResults);
        fuzzyResults.put("duckResults",duckduckResults);
        int x = 0;
        // MatlabUtils.calculateFuzzyRelevance();
        return fuzzyResults;
    }

    private void processDuckDuckGoPrecission(String keywords, String[] queryWords, List<DuckDuckLink> duckduckResults)
            throws InterruptedException, ExecutionException {
        List<Integer> distances = new ArrayList<>();
        List<Double> inputs1 = new ArrayList<>();
        List<Double> inputs2 = new ArrayList<>();

        List<String> filteredTexts= new ArrayList<>();
        List<String> filteredURLS = new ArrayList<>();
        List<String[]> items = new ArrayList<>();

        searchOnDuckDuckGo(filteredTexts, filteredURLS, keywords, queryWords);
        for(String it: filteredTexts){
            items.add(it.split(" "));
        }
        calculateAllInputs(items, queryWords, distances, inputs1, inputs2);
        recalculateInput2(inputs2, keywords.length());
        List<Double> relevance = matlabEngine.calculateFuzzyRelevance(inputs1,inputs2);
        for(int i = 0; i < filteredTexts.size();i++){
            String currentText = filteredTexts.get(i);
            String currentURL = filteredURLS.get(i);
            Double currentRelevance = relevance.get(i);
            duckduckResults.add(new DuckDuckLink(currentText,currentURL,currentRelevance));
        }
       
        //TODO:
        // loop the results from matlab and set the fuzzy relevance for each duckduck link
       // int x = 0;

    }
    // calculam levenshtein pentru titlu si pentru summary-ul fiecarui website
    private void processGooglePrecission(String keywords, String[] queryWords, List<GoogleSearchLink> googleResults)
            throws InterruptedException, ExecutionException {
        List<Result> chromeSearchResults;
        List<String[]> chromeResTitles = new ArrayList<>();
        List<String[]> chromeResSnippets = new ArrayList<>();
        List<Integer> distancesForTitles = new ArrayList<>();
        List<Integer> distancesForSnippets = new ArrayList<>();
        List<Double> inputs1ForTitles = new ArrayList<>();
        List<Double> inputs1ForSnippets = new ArrayList<>();
        List<Double> inputs2ForTitles = new ArrayList<>();
        List<Double> inputs2ForSnippets = new ArrayList<>();

        List<Integer> finalDistances = new ArrayList<>();
        List<Double> finalInputs1 = new ArrayList<>();
        List<Double> finalInputs2 = new ArrayList<>();

        chromeSearchResults = searchOnGoogle(keywords);

        for (Result res : chromeSearchResults) {
            String title = res.getTitle();
            String snippet = res.getSnippet();
            String url = res.getFormattedUrl();
            googleResults.add(new GoogleSearchLink(title,snippet,url,0.0));
            chromeResTitles.add(title.split(" "));
            chromeResSnippets.add(snippet.split(" "));
        }
        calculateAllInputs(chromeResTitles, queryWords, distancesForTitles, inputs1ForTitles, inputs2ForTitles);
        calculateAllInputs(chromeResSnippets, queryWords, distancesForSnippets, inputs1ForSnippets, inputs2ForSnippets);
        addTheValuesFromTitleAndSnippets(distancesForTitles, inputs1ForTitles, inputs2ForTitles, distancesForSnippets,
                inputs1ForSnippets, inputs2ForSnippets, finalDistances, finalInputs1, finalInputs2);
        recalculateInput2(finalInputs2, keywords.length());
        List<Double> relevance = (ArrayList) matlabEngine.calculateFuzzyRelevance(finalInputs1, finalInputs2);
        for(int i = 0; i<googleResults.size();i++){
            GoogleSearchLink crtLink = googleResults.get(i);
            Double crtRelevance = relevance.get(i);
            crtLink.setFuzzyRelevance(crtRelevance);
        }
        //TODO:
        // loop the results from matlab and set the fuzzy relevance for each google result

    }

    private void addTheValuesFromTitleAndSnippets(List<Integer> distancesTitle,
                                                  List<Double> inputs1Title, List<Double> inputs2Title,
                                                  List<Integer> distancesSnippets,
                                                  List<Double> inputs1Snippets, List<Double> inputs2Snippets,
                                                  List<Integer> finalDistances, List<Double> finalInputs1,
                                                  List<Double> finalInputs2
                                                  ) {
        // add all the distances together
        for(int i = 0; i < distancesTitle.size();i++){
            int dist1 = distancesTitle.get(i);
            int dist2 = distancesSnippets.get(i);
            int finalDist = dist1+dist2;
            finalDistances.add(finalDist);
        }

        for(int i = 0; i < inputs1Title.size(); i++){
            double input1Title = inputs1Title.get(i);
            double input1Snippet = inputs1Snippets.get(i);
            double finalInput1 = input1Snippet + input1Title;
            finalInputs1.add(finalInput1);
        }

        for(int i = 0; i < inputs2Title.size(); i++){
            double input2Title = inputs2Title.get(i);
            double input2Snippet = inputs2Snippets.get(i);
            double finalInput2 = input2Snippet + input2Title;
            finalInputs2.add(finalInput2);
        }
    }

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
    private double calculateInput1(int ed, String content){
        double itemLength = content.length();
        return (100 * (1 - ((double) ed / itemLength)));
    }
    // functie pentru a adauga 10% la valoarea curenta pentru a compensa posibile erori
    private void recalculateInput2(List<Double> finalInput2, int contentLength){
        for(int i = 0; i < finalInput2.size();i++){
            double currentInput2 = finalInput2.get(i);
            finalInput2.set(i, calculateInput2(currentInput2, contentLength));
        }
    }
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

    private List<Result> searchOnGoogle(String keywords) {
        Customsearch customsearch = null;
        try {
            customsearch = new Customsearch(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
                public void initialize(HttpRequest httpRequest) {
                    try {
                        // set connect and read timeouts
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
            list.setKey(GOOGLE_API_KEY);
            list.setCx(SEARCH_ENGINE_ID);
            Search results = list.execute();
            resultList = results.getItems();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }
    private void searchOnDuckDuckGo(List<String> filteredTexts, List<String> filteredURLS, String keywords,
                                    String[] queryWords){
        JSONObject json = null;
        JSONArray relatedTopics = null;
        try {
            JSONParser parser = new JSONParser();
            URL url = new URL("http://api.duckduckgo.com/?format=json&pretty=1&t=ClientName&q="+String.join("+",keywords.split(" ")));
            URLConnection conn = url.openConnection();
            // este necesara specificarea User-Agent-ului drept request property si in URL pentru a functiona
            conn.setRequestProperty("User-Agent", "ClientName");
            Scanner scn = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String buffer = scn.hasNext() ? scn.next() : "";
            json = (JSONObject)parser.parse(buffer);

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
        int x = 0;
        }
    }


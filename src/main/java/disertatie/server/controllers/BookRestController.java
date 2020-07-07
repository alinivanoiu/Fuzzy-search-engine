package disertatie.server.controllers;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.customsearch.Customsearch;
import com.google.api.services.customsearch.model.Result;
import com.google.api.services.customsearch.model.Search;

import disertatie.server.utils.Levenshtein;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.util.Scanner;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@RestController
@RequestMapping("/")
public class BookRestController {
    private int HTTP_REQUEST_TIMEOUT = 3 * 600000;
    private String GOOGLE_API_KEY = "AIzaSyCSI36qVUKUfDaV2DjSUSUDpdzrIlGxIfo";
    private String SEARCH_ENGINE_ID = "015970870679697835201:iskqgfupniq";
    private final static String DUCKDUCKGO_SEARCH_URL = "https://html.duckduckgo.com/html/?q=";

    @GetMapping(path = "/search/{keywords}")
    public Object search(@PathVariable String keywords) {
        String queryWords[] = keywords.split(" ");

        for(String word: queryWords){
            System.out.println(word);
        }

        processGooglePrecission(keywords, queryWords);


        return searchOnDuckDuckGo(keywords);
        //return searchOnGoogle(keywords);

    }
    private void processGooglePrecission(String keywords, String[] queryWords) {
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
            chromeResTitles.add(res.getTitle().split(" "));
            chromeResSnippets.add(res.getSnippet().split(" "));
        }
        calculateAllInputs(chromeResTitles, queryWords, distancesForTitles, inputs1ForTitles, inputs2ForTitles);
        calculateAllInputs(chromeResSnippets, queryWords, distancesForSnippets, inputs1ForSnippets, inputs2ForSnippets);
        addTheValuesFromTitleAndSnippets(distancesForTitles, inputs1ForTitles, inputs2ForTitles, distancesForSnippets,
                inputs1ForSnippets, inputs2ForSnippets, finalDistances, finalInputs1, finalInputs2);
        recalculateInput2(finalInputs2, keywords.length());
        int x = 0;
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
        //System.out.println(Levenshtein.distance(keywords, "Hello"));
    private double calculateInput1(int ed, String content){
        double itemLength = content.length();
        return (100 * (1 - ((double) ed / itemLength)));
    }
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
            // crestem cu 10% pentru a compensa pentru posibile erori
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
    private JSONObject searchOnDuckDuckGo(String keywords){
        JSONObject json = null;
        try {
            JSONParser parser = new JSONParser();
            URL url = new URL("http://api.duckduckgo.com/?format=json&pretty=1&t=ClientName&q="+keywords);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "ClientName");

            Scanner scn = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String buffer = scn.hasNext() ? scn.next() : "";
//           System.out.println(json.toString());
            json = (JSONObject)parser.parse(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
        }
    }

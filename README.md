# Fuzzy search engine

## Description
The Fuzzy search engine application was created in order to search the web for keywords on Google and Duck Duck Go in one go, the results would later on be processed using the Levenshtein distance algorithm and then sending the results to be processed by Matlab for relevance based on various fuzzy logic criterias. The final result would be shown in 2 different tabs (for each search engine API's results) as a table containing the URL, description and relevance percentage of each result.

The application is comprised of 2 separate components: frontend and backend.
This repository is the backend part of the application created using:
- Spring Boot
- REST API
- Google Search API 
- DuckDuck Go Search API
- Matlab Engine API for fuzzy logic processing of the web search results
- Levenshtein distance algorithm used as a relevance criteria

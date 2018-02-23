package uk.ac.ebi.subs.sheetloader;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UniRestWrapper {

    public HttpResponse<JsonNode> getJson(String uri, Map<String,String> headers){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(uri).headers(headers).asJson();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public HttpResponse<JsonNode> putJson(String uri, Map<String,String> headers, JSONObject json ){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.put(uri).headers(headers).body(json.toString()).asJson();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public HttpResponse<JsonNode> postJson(String uri, Map<String,String> headers, JSONObject json ){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.post(uri).headers(headers).body(json.toString()).asJson();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
        return response;
    }
}

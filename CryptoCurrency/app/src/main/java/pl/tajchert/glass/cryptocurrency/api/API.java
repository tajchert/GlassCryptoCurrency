package pl.tajchert.glass.cryptocurrency.api;

import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;


public class API {
    private static final String TAG = "API";
    private static final String API_TICKERS_URL = "https://api.bitcoinaverage.com/ticker/global/";
    private static final String API_HISTORY_URL_BEGIN = "https://api.bitcoinaverage.com/history/";
    private static final String API_HISTORY_URL_END = "/per_hour_monthly_sliding_window.csv";


    public static Ticker getTicker(String currency) {
        try {
            DefaultHttpClient http = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(API_TICKERS_URL + currency + "/");
            httpGet.setHeader("Accept", "application/json");
            httpGet.setHeader("Cache-Control", "no-cache");
            HttpResponse response = http.execute(httpGet);
            ResponseHandler<String> handler = new BasicResponseHandler();
            String body = handler.handleResponse(response);
            JSONObject myJson;
            try {
                myJson = new JSONObject(body);
                return jsonToTicker(myJson);
            } catch (JSONException e) {
                Log.d(TAG, "JSONException ", e);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        return null;
    }

    public static Ticker jsonToTicker(JSONObject jsonObject){
        if(jsonObject == null){
            return null;
        }
        Ticker ticker = new Ticker();
        try {
            ticker.setAsk(jsonObject.getDouble("ask"));
            ticker.setAvgDay(jsonObject.getDouble("24h_avg"));
            ticker.setBid(jsonObject.getDouble("bid"));
            ticker.setLast(jsonObject.getDouble("last"));
            ticker.setTimestamp(jsonObject.getString("timestamp"));
            ticker.setVolumeBtc(jsonObject.getDouble("volume_btc"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ticker;
    }

    public static void getHistoricalData(String currency){
        try {
            URL url = new URL(API_HISTORY_URL_BEGIN + currency + API_HISTORY_URL_END);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String line;
            String cvsSplitBy = ",";
            try {
                while ((line = in.readLine()) != null) {
                    String[] data = line.split(cvsSplitBy);
                    Log.d(TAG, "Value [date= " + data[0] + " , val=" + data[1] + "]");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (MalformedURLException e) {
            Log.d(TAG, "getHistoricalData : " + e);
        } catch (IOException e) {
            Log.d(TAG, "getHistoricalData : " + e);
        }
    }

}

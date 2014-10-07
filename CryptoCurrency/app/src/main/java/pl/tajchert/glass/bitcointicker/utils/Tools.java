package pl.tajchert.glass.bitcointicker.utils;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.ArrayList;

public class Tools {
    public static final String KEY_PREFS_LAST_VALUE = "KEY_PREFS_LAST_VALUE";
    public static final String KEY_PREFS_LAST_CURRENCY = "KEY_PREFS_LAST_CURRENCY";

    public static boolean isOnList(String currency){
        ArrayList<String> currencies = new ArrayList<String>();
        currencies.add("PLN");
        currencies.add("USD");
        currencies.add("AUD");
        currencies.add("BRL");
        currencies.add("CAD");
        currencies.add("CHF");
        currencies.add("CNY");
        currencies.add("EUR");
        currencies.add("GBP");
        currencies.add("HKD");
        currencies.add("IDR");
        currencies.add("ILS");
        currencies.add("MXN");
        currencies.add("NOK");
        currencies.add("NZD");
        currencies.add("RON");
        currencies.add("SEK");
        currencies.add("SGD");
        currencies.add("TRY");
        currencies.add("ZAR");
        if(currencies.contains(currency)){
            return true;
        }
        return false;
    }
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}

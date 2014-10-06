package pl.tajchert.glass.cryptocurrency;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.widget.RemoteViews;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.TreeMap;

import pl.tajchert.glass.cryptocurrency.api.API;
import pl.tajchert.glass.cryptocurrency.api.Ticker;
import pl.tajchert.glass.cryptocurrency.utils.Tools;

/**
 * A {@link Service} that publishes a {@link LiveCard} in the timeline.
 */
public class LiveCardService extends Service {
    private static final String LIVE_CARD_TAG = "LiveCardService";

    private final UpdateBinder mBinder = new UpdateBinder();
    private LiveCard mLiveCard;
    private String currency;
    private Ticker ticker;
    private RemoteViews remoteViews;

    private TreeMap<Long, Double> lastDayPrices = new TreeMap<Long, Double>();

    private SharedPreferences prefs;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = this.getSharedPreferences("pl.tajchert.glass.cryptocurrency", Context.MODE_PRIVATE);
        ArrayList<String> voiceResults;
        try {
            voiceResults = intent.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
        } catch (Exception e) {
            voiceResults = new ArrayList<String>();
        }
        if (mLiveCard == null) {
            mLiveCard = new LiveCard(this, LIVE_CARD_TAG);

            remoteViews = new RemoteViews(getPackageName(), R.layout.live_card);
            currency = "USD";
            if(voiceResults.size() == 1 && voiceResults.get(0).length() == 3){
                currency = voiceResults.get(0);
                //TODO check some list
            }
            cleanPrefs(currency);
            remoteViews.setTextViewText(R.id.cryptoName, "BTC - "+ currency);
            updateData(currency);
            mLiveCard.setViews(remoteViews);

            // Display the options menu when the live card is tapped.
            Intent menuIntent = new Intent(this, LiveCardMenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            mLiveCard.publish(PublishMode.REVEAL);
        } else {
            mLiveCard.navigate();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }

    private void updateData(String currency){
        new GetLatestTicker().execute(currency);
    }

    private class GetLatestTicker extends AsyncTask<String, Void, Ticker> {
        @Override
        protected Ticker doInBackground(String... params) {
            if(params[0] == null){
                return null;
            }
            API api = new API();
            ticker = API.getTicker(params[0]);
            //api.getHistoricalData(params[0]);
            lastDayPrices = api.getLast24H(params[0]);
            return ticker;
        }

        @Override
        protected void onPostExecute(Ticker result) {
            if(result != null) {
                Double prevVal = Double.parseDouble(prefs.getString(Tools.KEY_PREFS_LAST_VALUE, "0"));
                prefs.edit().putString(Tools.KEY_PREFS_LAST_VALUE, result.getLast().toString()).apply();

                if (mLiveCard != null && remoteViews != null) {
                    String tmp = result.getLast() + " " + currency;
                    remoteViews.setTextViewText(R.id.bottomPrice, tmp);
                    if(prevVal != 0) {
                        double percentRight = 0;
                        percentRight = ((result.getLast() - prevVal) / prevVal) * 100;
                        if(percentRight > 0 ){
                            remoteViews.setTextViewText(R.id.textRight, "+" + round(percentRight, 1) + "%");
                            remoteViews.setImageViewResource(R.id.arrowRight, R.drawable.arrow_up_colour);
                        } else if(percentRight == 0) {
                            remoteViews.setTextViewText(R.id.textRight, "0%");
                            remoteViews.setImageViewResource(R.id.arrowRight, R.drawable.no_change_regular_white);
                        } else {
                            remoteViews.setTextViewText(R.id.textRight, round(percentRight, 1) + "%");
                            remoteViews.setImageViewResource(R.id.arrowRight, R.drawable.arrow_down_colour);
                        }
                    } else {
                        remoteViews.setTextViewText(R.id.textRight, "");
                        remoteViews.setImageViewResource(R.id.arrowRight, R.drawable.no_change_regular_white);
                    }
                    Double prevDayVal;
                    if(lastDayPrices != null && lastDayPrices.size() > 0){
                        prevDayVal = lastDayPrices.firstEntry().getValue();
                        double percentLeft = 0;
                        percentLeft = ((result.getLast() - prevDayVal) / prevDayVal) * 100;

                        if(percentLeft > 0 ){
                            remoteViews.setTextViewText(R.id.textLeft, "+" + round(percentLeft, 1) + "%");
                            remoteViews.setImageViewResource(R.id.arrowLeft, R.drawable.arrow_up_colour);
                        } else if(percentLeft == 0){
                            remoteViews.setTextViewText(R.id.textLeft, "0%");
                            remoteViews.setImageViewResource(R.id.arrowLeft, R.drawable.no_change_regular_white);
                        } else {
                            remoteViews.setTextViewText(R.id.textLeft, round(percentLeft, 1) + "%");
                            remoteViews.setImageViewResource(R.id.arrowLeft, R.drawable.arrow_down_colour);
                        }
                    } else {
                        remoteViews.setTextViewText(R.id.textLeft, "");
                        remoteViews.setImageViewResource(R.id.arrowLeft, R.drawable.no_change_regular_white);
                    }
                    mLiveCard.setViews(remoteViews);
                }
            }
        }
    }

    private void cleanPrefs(String newCurrency){
        if(!prefs.getString(Tools.KEY_PREFS_LAST_CURRENCY, "").equals(newCurrency)){
            prefs.edit().putString(Tools.KEY_PREFS_LAST_VALUE, "0").apply();
            lastDayPrices = new TreeMap<Long, Double>();
            prefs.edit().putString(Tools.KEY_PREFS_LAST_CURRENCY, newCurrency).apply();
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public class UpdateBinder extends Binder {
        public void updateValues() {
            updateData(currency);
        }
    }
}

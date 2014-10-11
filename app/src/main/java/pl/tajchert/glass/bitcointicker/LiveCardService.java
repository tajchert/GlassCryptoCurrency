package pl.tajchert.glass.bitcointicker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;

import pl.tajchert.glass.bitcointicker.api.API;
import pl.tajchert.glass.bitcointicker.api.Ticker;
import pl.tajchert.glass.bitcointicker.utils.Tools;

/**
 * A {@link Service} that publishes a {@link LiveCard} in the timeline.
 */
public class LiveCardService extends Service {
    private static final String TAG = "LiveCardService";

    private final UpdateBinder mBinder = new UpdateBinder();
    private LiveCard mLiveCard;
    private String currency;
    private Ticker ticker;
    private RemoteViews remoteViews;

    private boolean chartVisible = false;
    private Bitmap bitmap;
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
            mLiveCard = new LiveCard(this, TAG);

            remoteViews = new RemoteViews(getPackageName(), R.layout.live_card);
            currency = "USD";
            if(voiceResults != null && voiceResults.size() == 1 && voiceResults.get(0).length() == 3){
                if(Tools.isOnList(voiceResults.get(0))){
                    currency = voiceResults.get(0);
                }
            }
            cleanPrefs(currency);
            remoteViews.setTextViewText(R.id.cryptoName, "BTC - "+ currency);
            if(Tools.isNetworkAvailable(this)){
                remoteViews.setViewVisibility(R.id.noInternetConnection, View.INVISIBLE);
                updateData(currency);
            } else {
                remoteViews.setViewVisibility(R.id.noInternetConnection, View.VISIBLE);
            }

            mLiveCard.setViews(remoteViews);

            // Display the options menu when the live card is tapped.
            Intent menuIntent = new Intent(this, LiveCardMenuActivity.class);
            if(mLiveCard.isPublished()){
                mLiveCard.unpublish();
            }
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            mLiveCard.publish(PublishMode.REVEAL);
        } else {
            mLiveCard.navigate();
        }
        scheduleUpdate();
        return START_STICKY;
    }

    private void scheduleUpdate(){
        Intent intent = new Intent(LiveCardService.this, LiveCardService.class);
        PendingIntent serviceIntent = PendingIntent.getService(LiveCardService.this, 0, intent, 0);
        AlarmManager alarm_manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm_manager.setRepeating(AlarmManager.RTC, Calendar.getInstance().getTimeInMillis(), 900000,  serviceIntent);
    }

    private void cancelUpdate(){
        AlarmManager alarmManager = (AlarmManager) LiveCardService.this.getSystemService(Context.ALARM_SERVICE);
        Intent updateServiceIntent = new Intent(LiveCardService.this, LiveCardService.class);
        PendingIntent pendingUpdateIntent = PendingIntent.getService(LiveCardService.this, 0, updateServiceIntent, 0);
        try {
            alarmManager.cancel(pendingUpdateIntent);
        } catch (Exception e) {
            Log.e(TAG, "AlarmManager update was not canceled. " + e.toString());
        }
    }

    @Override
    public void onDestroy() {
        cancelUpdate();
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }

    private void updateData(String currency){
        new GetLatestTicker().execute(currency);
    }

    private class GetChartData extends AsyncTask<String, Void, TreeMap<Long, Double>> {
        @Override
        protected TreeMap<Long, Double> doInBackground(String... params) {
            if (params[0] == null) {
                return null;
            }
            API api = new API();
            lastDayPrices = api.getLast24H(params[0]);
            return lastDayPrices;
        }
        @Override
        protected void onPostExecute(TreeMap<Long, Double> result) {
            bitmap = createGraph(lastDayPrices);
            if(bitmap != null){
                remoteViews.setImageViewBitmap(R.id.chart, bitmap);
            }
        }
    }

    private class GetLatestTicker extends AsyncTask<String, Void, Ticker> {
        @Override
        protected Ticker doInBackground(String... params) {
            if(params[0] == null){
                return null;
            }
            API api = new API();
            ticker = API.getTicker(params[0]);
            return ticker;
        }

        @Override
        protected void onPostExecute(Ticker result) {
            if(result != null) {
                Double prevVal = Double.parseDouble(prefs.getString(Tools.KEY_PREFS_LAST_VALUE, "0"));
                prefs.edit().putString(Tools.KEY_PREFS_LAST_VALUE, result.getLast().toString()).apply();
                if (mLiveCard != null && remoteViews != null) {
                    remoteViews.setTextViewText(R.id.bottomPrice, result.getLast() + "");
                    if(prevVal != 0) {
                        double percentRight = round((((result.getLast() - prevVal) / prevVal) * 100), 1);
                        if(percentRight > 0 ){
                            remoteViews.setTextViewText(R.id.textUpdate, "(+" + percentRight + "%)");
                            remoteViews.setTextColor(R.id.textUpdate, Color.GREEN);
                        } else if(percentRight == 0) {
                            remoteViews.setTextViewText(R.id.textUpdate, "(0%)");
                            remoteViews.setTextColor(R.id.textUpdate, Color.WHITE);
                        } else {
                            remoteViews.setTextViewText(R.id.textUpdate, "(" + percentRight + "%)");
                            remoteViews.setTextColor(R.id.textUpdate, Color.RED);
                        }
                    } else {
                        remoteViews.setTextViewText(R.id.textUpdate, "");
                    }
                    Double prevDayVal;
                    if(lastDayPrices != null && lastDayPrices.size() > 0){
                        prevDayVal = lastDayPrices.firstEntry().getValue();

                        double percentLeft = round((((result.getLast() - prevDayVal) / prevDayVal) * 100), 1);
                        if(percentLeft > 0 ){
                            remoteViews.setTextViewText(R.id.textDay, "+" + percentLeft + "%");
                            remoteViews.setTextColor(R.id.textDay, Color.GREEN);
                        } else if(percentLeft == 0){
                            remoteViews.setTextViewText(R.id.textDay, "0%");
                            remoteViews.setTextColor(R.id.textDay, Color.WHITE);
                        } else {
                            remoteViews.setTextViewText(R.id.textDay, percentLeft + "%");
                            remoteViews.setTextColor(R.id.textDay, Color.RED);
                        }
                    } else {
                        remoteViews.setTextColor(R.id.textDay, Color.WHITE);
                    }
                    mLiveCard.setViews(remoteViews);
                    new GetChartData().execute(currency);
                }
            }
        }
    }

    public void chartChangeVisibility(){
        if(remoteViews != null && mLiveCard != null){
            if(chartVisible){
                remoteViews.setViewVisibility(R.id.chartTitle, View.INVISIBLE);
                remoteViews.setViewVisibility(R.id.chart, View.INVISIBLE);
                chartVisible = false;
            } else {
                remoteViews.setViewVisibility(R.id.chartTitle, View.VISIBLE);
                remoteViews.setViewVisibility(R.id.chart, View.VISIBLE);
                chartVisible = true;
            }
            mLiveCard.setViews(remoteViews);
        }
    }

    private Bitmap createGraph(TreeMap<Long, Double> prices){
        XYSeries series = new XYSeries("");
        Double min = Double.MAX_VALUE;
        Double max = 0d;
        Long maxDate = 0l;
        Long minDate = 0l;
        for(Map.Entry<Long, Double> entry : prices.entrySet()) {
            series.add(entry.getKey(), entry.getValue());
            if(entry.getValue() > max){
                max = entry.getValue();
                maxDate = entry.getKey();
            } else if (entry.getValue() < min){
                min = entry.getValue();
                minDate = entry.getKey();
            }
        }
        XYSeriesRenderer renderer = new XYSeriesRenderer();
        renderer.setLineWidth(2);
        renderer.setColor(Color.RED);
        XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
        mRenderer.addSeriesRenderer(renderer);
        mRenderer.setPanEnabled(false, false);
        mRenderer.setYAxisMax(max);
        mRenderer.setYAxisMin(min);
        mRenderer.setYTitle(currency + "");
        mRenderer.setXTitle("Time");
        mRenderer.addYTextLabel(max, ((int) Math.round(max)) + " " + currency);//TODO format
        mRenderer.setShowGrid(false);
        mRenderer.setXLabels(0);
        mRenderer.setYLabels(0);
        mRenderer.setShowLegend(false);
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        dataset.addSeries(series);
        GraphicalView chartView = ChartFactory.getLineChartView(this, dataset, mRenderer);
        return loadBitmapFromView(chartView);
    }

    public static Bitmap loadBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap(600, 250, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        v.draw(c);
        return b;
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
        public void showChart(){
            chartChangeVisibility();
        }
    }
}

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
import android.util.Log;
import android.util.TypedValue;
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;

import pl.tajchert.glass.bitcointicker.api.API;
import pl.tajchert.glass.bitcointicker.utils.Tools;

/**
 * A {@link Service} that publishes a {@link LiveCard} in the timeline.
 */
public class LiveCardService extends Service {
    private static final String TAG = "LiveCardService";

    public static final String CURRENCY_POSITION_KEY = "CURRENCY_POSITION_KEY";
    public static final String ACTION_START_KEY = "ACTION_START_KEY";

    private final UpdateBinder mBinder = new UpdateBinder();
    private LiveCard mLiveCard;
    private RemoteViews remoteViews;
    private boolean isAlarmUp = false;

    private boolean chartVisible = false;
    private TreeMap<Long, Double> lastDayPrices = new TreeMap<Long, Double>();

    private SharedPreferences prefs;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = this.getSharedPreferences("pl.tajchert.glass.cryptocurrency", Context.MODE_PRIVATE);
        String action = null;
        try {
            action = intent.getAction();
        } catch (Exception e) {
            //called by AlarmManger
        }
        if (action != null && action.equals(ACTION_START_KEY)) {
            cancelUpdate();
            int currencyPosition = intent.getIntExtra(CURRENCY_POSITION_KEY, 0);
            BitcoinTicker.currency = Tools.getCurrencyList().get(currencyPosition);
        } else if (action == null) {
            //called by alarmManager
        }
        cleanPrefs(BitcoinTicker.currency);

        if (mLiveCard == null) {
            mLiveCard = new LiveCard(this, TAG);
            remoteViews = new RemoteViews(getPackageName(), R.layout.live_card);

            Intent menuIntent = new Intent(this, LiveCardMenuActivity.class);
            if (mLiveCard.isPublished()) {
                mLiveCard.unpublish();
            }
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            mLiveCard.publish(PublishMode.REVEAL);
        } else {
            if (remoteViews == null) {
                remoteViews = new RemoteViews(getPackageName(), R.layout.live_card);
            }
        }
        remoteViews.setTextViewText(R.id.cryptoName, "BTC - " + BitcoinTicker.currency);
        if (Tools.isNetworkAvailable(this)) {
            remoteViews.setViewVisibility(R.id.noInternetConnection, View.INVISIBLE);
            updateData(BitcoinTicker.currency);
        } else {
            remoteViews.setViewVisibility(R.id.noInternetConnection, View.VISIBLE);
        }
        mLiveCard.setViews(remoteViews);
        if (!isAlarmUp) {
            cancelUpdate();
            scheduleUpdate();
        }
        if (action != null && action.equals(ACTION_START_KEY)) {
            remoteViews.setViewVisibility(R.id.chartLayout, View.INVISIBLE);
            chartVisible = false;
            mLiveCard.navigate();
        }
        return START_STICKY;
    }

    private void scheduleUpdate() {
        isAlarmUp = true;
        Intent intent = new Intent(LiveCardService.this, LiveCardService.class);
        PendingIntent serviceIntent = PendingIntent.getService(LiveCardService.this, 0, intent, 0);
        AlarmManager alarm_manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm_manager.setRepeating(AlarmManager.RTC, Calendar.getInstance().getTimeInMillis(), 900000, serviceIntent);//15 minutes
    }

    private void cancelUpdate() {
        isAlarmUp = false;
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

    private void updateData(String currency) {
        new GetChartData().execute(currency);
    }

    private class GetChartData extends AsyncTask<String, Void, TreeMap<Long, Double>> {
        private Double lastPrice;
        private Double dayAgoPrice;

        @Override
        protected TreeMap<Long, Double> doInBackground(String... params) {
            if (params[0] == null) {
                return null;
            }
            API api = new API();
            lastDayPrices = api.getLast24H(params[0]);
            dayAgoPrice = lastDayPrices.firstEntry().getValue();
            lastPrice = lastDayPrices.lastEntry().getValue();

            Log.d(TAG, "doInBackground dayAgoPrice: " + dayAgoPrice + ", lastPrice: " + lastPrice);
            return lastDayPrices;
        }

        @Override
        protected void onPostExecute(TreeMap<Long, Double> result) {
            setData(lastPrice, dayAgoPrice);
            createGraph(lastDayPrices);
        }
    }

    private void setData(Double lastPrice, Double dayAgoPrice) {
        Double prevVal = Double.parseDouble(prefs.getString(Tools.KEY_PREFS_LAST_VALUE, "0"));
        prefs.edit().putString(Tools.KEY_PREFS_LAST_VALUE, lastPrice.toString()).apply();
        if (mLiveCard != null && remoteViews != null) {
            remoteViews.setTextViewText(R.id.bottomPrice, lastPrice.toString());//result.getLast() +
            if (lastPrice.toString().length() >= 8) {
                remoteViews.setTextViewTextSize(R.id.bottomPrice, TypedValue.COMPLEX_UNIT_SP, 55);
            }
            if (lastPrice.toString().length() >= 10) {
                remoteViews.setTextViewTextSize(R.id.bottomPrice, TypedValue.COMPLEX_UNIT_SP, 50);
            }
            if (lastPrice.toString().length() >= 12) {
                remoteViews.setTextViewTextSize(R.id.bottomPrice, TypedValue.COMPLEX_UNIT_SP, 40);
            }
            if(prevVal != 0) {
                double percentRight = round((((lastPrice - prevVal) / prevVal) * 100), 1);
                if (percentRight > 0) {
                    remoteViews.setTextViewText(R.id.textUpdate, "(+" + percentRight + "%)");
                    remoteViews.setTextColor(R.id.textUpdate, Color.GREEN);
                } else if (percentRight == 0) {
                    remoteViews.setTextViewText(R.id.textUpdate, "(0%)");
                    remoteViews.setTextColor(R.id.textUpdate, Color.WHITE);
                } else {
                    remoteViews.setTextViewText(R.id.textUpdate, "(" + percentRight + "%)");
                    remoteViews.setTextColor(R.id.textUpdate, Color.RED);
                }
            }
            double percentLastDay = round((((lastPrice - dayAgoPrice) / dayAgoPrice) * 100), 1);
            if (percentLastDay > 0) {
                remoteViews.setTextViewText(R.id.textDay, "+" + percentLastDay + "%");
                remoteViews.setTextViewText(R.id.chartChange, "+" + percentLastDay + "%");
                remoteViews.setTextColor(R.id.chartChange, Color.GREEN);
                remoteViews.setTextColor(R.id.textDay, Color.GREEN);
            } else if (percentLastDay == 0) {
                remoteViews.setTextViewText(R.id.textDay, "0%");
                remoteViews.setTextViewText(R.id.chartChange, "0%");
                remoteViews.setTextColor(R.id.chartChange, Color.WHITE);
                remoteViews.setTextColor(R.id.textDay, Color.WHITE);
            } else {
                remoteViews.setTextViewText(R.id.textDay, percentLastDay + "%");
                remoteViews.setTextColor(R.id.textDay, Color.RED);
                remoteViews.setTextViewText(R.id.chartChange, percentLastDay + "%");
                remoteViews.setTextColor(R.id.chartChange, Color.RED);
            }
            remoteViews.setTextViewText(R.id.timestamp, new SimpleDateFormat("h:mm a z").format(Calendar.getInstance().getTime()) + "");
            mLiveCard.setViews(remoteViews);
        }
    }

    public void chartChangeVisibility() {
        if (remoteViews != null && mLiveCard != null) {
            if (chartVisible) {
                remoteViews.setViewVisibility(R.id.chartLayout, View.INVISIBLE);
                chartVisible = false;
            } else {
                remoteViews.setViewVisibility(R.id.chartLayout, View.VISIBLE);
                chartVisible = true;
            }
            mLiveCard.setViews(remoteViews);
        }
    }

    private void refreshGraphView(double max, Bitmap bitmap) {
        if (remoteViews != null && mLiveCard != null) {
            remoteViews.setTextViewText(R.id.chartMaxY, ((int) Math.round(max)) + "");
            remoteViews.setTextViewText(R.id.chartTitleY, "Rate (" + BitcoinTicker.currency + ")");
            if (bitmap != null) {
                remoteViews.setImageViewBitmap(R.id.chart, bitmap);
            }
            mLiveCard.setViews(remoteViews);
        }
    }

    private void createGraph(TreeMap<Long, Double> prices) {
        XYSeries series = new XYSeries("");
        Double min = Double.MAX_VALUE;
        Double max = 0d;
        for (Map.Entry<Long, Double> entry : prices.entrySet()) {
            series.add(entry.getKey(), entry.getValue());
            if (entry.getValue() > max) {
                max = entry.getValue();
            } else if (entry.getValue() < min) {
                min = entry.getValue();
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
        mRenderer.setShowGrid(false);
        mRenderer.setXLabels(0);
        mRenderer.setYLabels(0);
        mRenderer.setShowLegend(false);
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        dataset.addSeries(series);
        GraphicalView chartView = ChartFactory.getLineChartView(this, dataset, mRenderer);
        refreshGraphView(max, loadBitmapFromView(chartView));
    }

    public static Bitmap loadBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap(600, 250, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        v.draw(c);
        return b;
    }

    private void cleanPrefs(String newCurrency) {
        if (!prefs.getString(Tools.KEY_PREFS_LAST_CURRENCY, "").equals(newCurrency)) {
            prefs.edit().putString(Tools.KEY_PREFS_LAST_VALUE, "0").apply();
            lastDayPrices = new TreeMap<Long, Double>();
            prefs.edit().putString(Tools.KEY_PREFS_LAST_CURRENCY, newCurrency).apply();
            cancelUpdate();
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
            updateData(BitcoinTicker.currency);
        }

        public void showChart() {
            chartChangeVisibility();
        }
    }
}

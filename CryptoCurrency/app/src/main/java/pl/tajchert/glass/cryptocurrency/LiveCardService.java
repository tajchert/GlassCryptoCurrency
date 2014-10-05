package pl.tajchert.glass.cryptocurrency;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.widget.RemoteViews;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import java.util.ArrayList;

import pl.tajchert.glass.cryptocurrency.api.API;
import pl.tajchert.glass.cryptocurrency.api.Ticker;

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

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ArrayList<String> voiceResults = intent.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
        if (mLiveCard == null) {
            mLiveCard = new LiveCard(this, LIVE_CARD_TAG);

            remoteViews = new RemoteViews(getPackageName(), R.layout.live_card);
            currency = "USD";
            if(voiceResults.size() == 1 && voiceResults.get(0).length() == 3){
                currency = voiceResults.get(0);
                //TODO check some list
            }
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
            ticker = API.getTicker(params[0]);
            API.getHistoricalData("PLN");
            return ticker;
        }

        @Override
        protected void onPostExecute(Ticker result) {
            if(mLiveCard != null && remoteViews != null && result != null){
                String tmp = result.getLast() + " " + currency;
                remoteViews.setTextViewText(R.id.bottomPrice, tmp );
                mLiveCard.setViews(remoteViews);
            }
        }
    }
    public class UpdateBinder extends Binder {
        public void updateValues() {
            updateData(currency);
        }
    }
}

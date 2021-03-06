package pl.tajchert.glass.bitcointicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;

/**
 * A transparent {@link Activity} displaying a "Stop" options menu to remove the {@link LiveCard}.
 */
public class LiveCardMenuActivity extends Activity {
    private Menu menu;
    private LiveCardService.UpdateBinder updateBinder;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof LiveCardService.UpdateBinder) {
                updateBinder = (LiveCardService.UpdateBinder) service;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Do nothing.
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Open the options menu right away.
        openOptionsMenu();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(new Intent(this, LiveCardService.class), mConnection, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.live_card, menu);
        this.menu = menu;
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unbindService(mConnection);
    }

    private void updateMenuTitle( MenuItem chartMenuItem) {
        if (BitcoinTicker.isChartVisible) {
            chartMenuItem.setTitle("View rate");
            chartMenuItem.setIcon(R.drawable.ic_rate);
        } else {
            chartMenuItem.setTitle("View chart");
            chartMenuItem.setIcon(R.drawable.ic_chart);
        }
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        if(menu != null){
            updateMenuTitle(menu.findItem(R.id.action_chart));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_stop:
                // Stop the service which will unpublish the live card.
                stopService(new Intent(this, LiveCardService.class));
                return true;
            case R.id.action_chart:
                if(BitcoinTicker.isChartVisible == false) {
                    BitcoinTicker.isChartVisible = true;
                } else {
                    BitcoinTicker.isChartVisible = false;
                }
                showChart();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showChart() {
        if(updateBinder != null) {
            updateBinder.showChart();
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        // Nothing else to do, finish the Activity.
        finish();
    }

    private void updateLiveCard() {
        if(updateBinder != null) {
            updateBinder.updateValues();
        }
    }
}

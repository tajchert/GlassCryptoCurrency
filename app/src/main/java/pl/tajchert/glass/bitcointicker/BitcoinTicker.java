package pl.tajchert.glass.bitcointicker;

import android.app.Application;
import android.content.res.Configuration;

public class BitcoinTicker extends Application {

    private static BitcoinTicker singleton;
    public static boolean isChartVisible = false;

    public BitcoinTicker getInstance(){
        return singleton;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

}

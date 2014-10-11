/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.tajchert.glass.bitcointicker;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.touchpad.GestureDetector.BaseListener;
import com.google.android.glass.touchpad.GestureDetector.ScrollListener;

import java.util.ArrayList;
import java.util.Calendar;

import pl.tajchert.glass.bitcointicker.utils.Tools;

/**
 * Activity to set the timer.
 */
public class SetCurrencyActivity extends Activity implements BaseListener, ScrollListener{
    private static final String TAG = "SetCurrencyActivity";
    private static final float MAX_DRAG_VELOCITY = 1;

    private ValueAnimator mInertialScrollAnimator;
    private Long touchTime;

    private int position;
    private ArrayList<String> currencies;

    private TextView mCurrTop;
    private TextView mCurrMid;
    private TextView mCurrBot;
    private TextView mTipView;

    private AudioManager mAudioManager;
    private GestureDetector mDetector;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currencies = Tools.getCurrencyList();

        mDetector = new GestureDetector(this)
                .setBaseListener(this)
                .setScrollListener(this);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Initialize the various views.
        setContentView(R.layout.set_card);
        mCurrTop = (TextView) findViewById(R.id.topCurr);
        mCurrMid = (TextView) findViewById(R.id.midCurr);
        mCurrBot = (TextView) findViewById(R.id.bottCurr);
        mTipView = (TextView) findViewById(R.id.tip);

        mCurrBot.setText("USD");
        mTipView.setText(getResources().getString(R.string.swipe_to_set_currency));
        updateText();
        touchTime = Calendar.getInstance().getTimeInMillis();

        // Initialize the animator use for the intertial scrolling.
        mInertialScrollAnimator = new ValueAnimator();
        mInertialScrollAnimator.setInterpolator(new DecelerateInterpolator());
        mInertialScrollAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();
                setNewPosition(Math.round(value));
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mInertialScrollAnimator.cancel();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mDetector.onMotionEvent(event);
    }
    @Override
    public boolean onScroll(float displacement, float delta, float velocity) {
        touchTime = Calendar.getInstance().getTimeInMillis();
        addFlingValue(Math.min(velocity, MAX_DRAG_VELOCITY));
        return true;
    }

    @Override
    public boolean onGesture(Gesture gesture) {
        if(Calendar.getInstance().getTimeInMillis() - touchTime < 200){
            return false;
        }
        switch (gesture) {
            case TAP:
                playSoundEffect(Sounds.TAP);
                forceEndAnimation();
                startTicker();
                finish();
                return true;
            case SWIPE_DOWN:
                setResultInternal(RESULT_CANCELED, null);
                playSoundEffect(Sounds.DISMISSED);
                forceEndAnimation();
                finish();
                return true;
            default:
                return false;
        }
    }

    private void startTicker() {
        Intent tickerIntent = new Intent(this, LiveCardService.class);
        tickerIntent.putExtra(LiveCardService.CURRENCY_POSITION_KEY, position);
        tickerIntent.setAction(LiveCardService.ACTION_START_KEY);
        startService(tickerIntent);
    }

    private void addFlingValue(float delta) {
        setNewPosition(Math.round(position + delta));
    }

    private void setNewPosition(int newPosition) {
        int prevPosition = position;

        newPosition = checkNewPositionNumber(newPosition);

        if(newPosition != prevPosition){
            Log.d(TAG, "setNewPosition position change");
            position = newPosition;
            playSoundEffect(Sounds.TAP);
            updateText();
        }
    }

    private int checkNewPositionNumber(int newPosition){
        if(newPosition < 0){
            newPosition = 0;
        } else if (newPosition >= Tools.getCurrencyList().size()){
            newPosition = (Tools.getCurrencyList().size() - 1);
        }
        return newPosition;
    }

    private void updateText() {
        if(position == 0){
            mCurrTop.setText("");
        } else {
            mCurrTop.setText(Tools.getCurrencyList().get(position - 1));
        }

        mCurrMid.setText(Tools.getCurrencyList().get(position));

        if(position >= (Tools.getCurrencyList().size()-1)){
            mCurrBot.setText("");
        } else {
            mCurrBot.setText(Tools.getCurrencyList().get(position + 1));
        }
        mTipView.setVisibility(View.INVISIBLE);
    }

    protected void playSoundEffect(int soundId) {
        mAudioManager.playSoundEffect(soundId);
    }


    protected void setResultInternal(int resultCode, Intent resultIntent) {
        setResult(resultCode, resultIntent);
    }

    void forceEndAnimation() {
        if (mInertialScrollAnimator.isRunning()) {
            mInertialScrollAnimator.end();
        }
    }
}

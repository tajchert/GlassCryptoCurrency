package pl.tajchert.glass.bitcointicker.api;

import com.google.gson.Gson;

import java.util.Date;

public class Ticker {

    private Double avgDay;
    private Double ask;
    private Double bid;
    private Double last;
    private String timestamp;
    private Date date;
    private Double volumeBtc;

    public Ticker() {
    }

    public Ticker(Double avgDay, Date date) {
        this.avgDay = avgDay;
        this.date = date;
    }

    public Double getAvgDay() {
        return avgDay;
    }

    public void setAvgDay(Double avgDay) {
        this.avgDay = avgDay;
    }

    public Double getAsk() {
        return ask;
    }

    public void setAsk(Double ask) {
        this.ask = ask;
    }

    public Double getBid() {
        return bid;
    }

    public void setBid(Double bid) {
        this.bid = bid;
    }

    public Double getLast() {
        return last;
    }

    public void setLast(Double last) {
        this.last = last;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Double getVolumeBtc() {
        return volumeBtc;
    }

    public void setVolumeBtc(Double volumeBtc) {
        this.volumeBtc = volumeBtc;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String toJSON(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
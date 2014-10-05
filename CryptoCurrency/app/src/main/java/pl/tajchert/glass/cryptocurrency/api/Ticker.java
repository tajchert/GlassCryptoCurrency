package pl.tajchert.glass.cryptocurrency.api;

import com.google.gson.Gson;

public class Ticker {

    private Double avgDay;
    private Double ask;
    private Double bid;
    private Double last;
    private String timestamp;
    private Double volumeBtc;

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

    public String toJSON(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
package org.thosp.yourlocalweather;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thosp.yourlocalweather.model.WeatherForecast;
import org.thosp.yourlocalweather.utils.AppPreference;
import org.thosp.yourlocalweather.utils.Constants;
import org.thosp.yourlocalweather.utils.CustomValueFormatter;
import org.thosp.yourlocalweather.utils.LanguageUtil;
import org.thosp.yourlocalweather.utils.PreferenceUtil;
import org.thosp.yourlocalweather.utils.XAxisValueFormatter;
import org.thosp.yourlocalweather.utils.YAxisValueFormatter;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cz.msebera.android.httpclient.Header;

import static org.thosp.yourlocalweather.utils.LogToFile.appendLog;
import static org.thosp.yourlocalweather.utils.Utils.getWeatherForecastUrl;


public class GraphsActivity extends BaseActivity {

    private static final String TAG = "GraphsActivity";

    private static AsyncHttpClient client = new AsyncHttpClient();

    private ConnectionDetector mConnectionDetector;
    public List<WeatherForecast> mForecastList;
    private LineChart mTemperatureChart;
    private LineChart mWindChart;
    private LineChart mRainChart;
    private LineChart mSnowChart;
    private String[] mDatesArray;
    private Handler mHandler;
    private ProgressDialog mGetWeatherProgress;
    private CustomValueFormatter mValueFormatter;
    private YAxisValueFormatter mYAxisFormatter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphs);
        mConnectionDetector = new ConnectionDetector(this);
        mForecastList = new ArrayList<>();
        mGetWeatherProgress = getProgressDialog();
        mValueFormatter = new CustomValueFormatter();
        mYAxisFormatter = new YAxisValueFormatter();
        mTemperatureChart = (LineChart) findViewById(R.id.temperature_chart);
        mWindChart = (LineChart) findViewById(R.id.wind_chart);
        mRainChart = (LineChart) findViewById(R.id.rain_chart);
        mSnowChart = (LineChart) findViewById(R.id.snow_chart);
        TextView temperatureLabel = (TextView) findViewById(R.id.graphs_temperature_label);
        temperatureLabel.setText(getString(R.string.label_temperature) +
                                         ", " +
                                         AppPreference.getTemperatureUnit(this));
        TextView windLabel = (TextView) findViewById(R.id.graphs_wind_label);
        windLabel.setText(getString(R.string.label_wind) + ", " + AppPreference.getWindUnit(this));
        TextView rainLabel = (TextView) findViewById(R.id.graphs_rain_label);
        rainLabel.setText(getString(R.string.label_rain) + ", " + getString(R.string.millimetre_label));
        TextView snowLabel = (TextView) findViewById(R.id.graphs_snow_label);
        snowLabel.setText(getString(R.string.label_snow) + ", " + getString(R.string.millimetre_label));

        updateUI();
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case Constants.TASK_RESULT_ERROR:
                        Toast.makeText(GraphsActivity.this,
                                       R.string.toast_parse_error,
                                       Toast.LENGTH_SHORT).show();
                        setVisibleUpdating(false);
                        break;
                    case Constants.PARSE_RESULT_ERROR:
                        Toast.makeText(GraphsActivity.this,
                                       R.string.toast_parse_error,
                                       Toast.LENGTH_SHORT).show();
                        setVisibleUpdating(false);
                        break;
                    case Constants.PARSE_RESULT_SUCCESS:
                        setVisibleUpdating(false);
                        updateUI();
                        if (!mForecastList.isEmpty()) {
                            AppPreference.saveWeatherForecast(GraphsActivity.this,
                                                              mForecastList);
                        }
                        break;
                }
            }
        };

        ScrollView mRecyclerView = (ScrollView) findViewById(R.id.graph_scroll_view);
        mRecyclerView.setOnTouchListener(new ActivityTransitionTouchListener(
                WeatherForecastActivity.class,
                null, this));
    }

    private void setTemperatureChart() {
        mTemperatureChart.setDescription("");
        mTemperatureChart.setDrawGridBackground(false);
        mTemperatureChart.setTouchEnabled(true);
        mTemperatureChart.setDragEnabled(true);
        mTemperatureChart.setMaxHighlightDistance(300);
        mTemperatureChart.setPinchZoom(true);
        mTemperatureChart.getLegend().setEnabled(false);

        formatDate();
        XAxis x = mTemperatureChart.getXAxis();
        x.setEnabled(true);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setValueFormatter(new XAxisValueFormatter(mDatesArray));

        YAxis yLeft = mTemperatureChart.getAxisLeft();
        yLeft.setEnabled(true);
        yLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yLeft.setDrawAxisLine(false);
        yLeft.setDrawGridLines(true);
        yLeft.enableGridDashedLine(5f, 10f, 0f);
        yLeft.setGridColor(Color.parseColor("#333333"));
        yLeft.setXOffset(15);
        yLeft.setValueFormatter(mYAxisFormatter);

        mTemperatureChart.getAxisRight().setEnabled(false);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < mForecastList.size(); i++) {
            float temperatureDay = AppPreference.getTemperature(this, mForecastList.get(i).getTemperatureDay());
            entries.add(new Entry(i, temperatureDay));
        }

        LineDataSet set;
        if (mTemperatureChart.getData() != null) {
            mTemperatureChart.getData().removeDataSet(mTemperatureChart.getData().getDataSetByIndex(
                    mTemperatureChart.getData().getDataSetCount() - 1));
            set = new LineDataSet(entries, "Day");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setDrawValues(false);
            set.setValueTextSize(12f);
            set.setColor(Color.parseColor("#E84E40"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mTemperatureChart.setData(data);
        } else {
            set = new LineDataSet(entries, "Day");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#E84E40"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mTemperatureChart.setData(data);
        }
        mTemperatureChart.invalidate();
    }
    
    private void setWindChart() {
        mWindChart.setDescription("");
        mWindChart.setDrawGridBackground(false);
        mWindChart.setTouchEnabled(true);
        mWindChart.setDragEnabled(true);
        mWindChart.setMaxHighlightDistance(300);
        mWindChart.setPinchZoom(true);
        mWindChart.getLegend().setEnabled(false);

        formatDate();
        XAxis x = mWindChart.getXAxis();
        x.setEnabled(true);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setValueFormatter(new XAxisValueFormatter(mDatesArray));

        YAxis yLeft = mWindChart.getAxisLeft();
        yLeft.setEnabled(true);
        yLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yLeft.setDrawAxisLine(false);
        yLeft.setDrawGridLines(true);
        yLeft.enableGridDashedLine(5f, 10f, 0f);
        yLeft.setGridColor(Color.parseColor("#333333"));
        yLeft.setXOffset(15);
        yLeft.setValueFormatter(mYAxisFormatter);

        mWindChart.getAxisRight().setEnabled(false);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < mForecastList.size(); i++) {
            float wind = AppPreference.getWind(this, mForecastList.get(i).getWindSpeed());
            entries.add(new Entry(i, wind));
        }

        LineDataSet set;
        if (mWindChart.getData() != null) {
            mWindChart.getData().removeDataSet(mWindChart.getData().getDataSetByIndex(
                    mWindChart.getData().getDataSetCount() - 1));
            set = new LineDataSet(entries, "Wind");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#00BCD4"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mWindChart.setData(data);
        } else {
            set = new LineDataSet(entries, "Wind");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#00BCD4"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mWindChart.setData(data);
        }
        mWindChart.invalidate();
    }

    private void setRainChart() {
        mRainChart.setDescription("");
        mRainChart.setDrawGridBackground(false);
        mRainChart.setTouchEnabled(true);
        mRainChart.setDragEnabled(true);
        mRainChart.setMaxHighlightDistance(300);
        mRainChart.setPinchZoom(true);
        mRainChart.getLegend().setEnabled(false);

        formatDate();
        XAxis x = mRainChart.getXAxis();
        x.setEnabled(true);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setValueFormatter(new XAxisValueFormatter(mDatesArray));

        YAxis yLeft = mRainChart.getAxisLeft();
        yLeft.setEnabled(true);
        yLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yLeft.setDrawAxisLine(false);
        yLeft.setDrawGridLines(true);
        yLeft.enableGridDashedLine(5f, 10f, 0f);
        yLeft.setGridColor(Color.parseColor("#333333"));
        yLeft.setXOffset(15);
        yLeft.setValueFormatter(mYAxisFormatter);

        mRainChart.getAxisRight().setEnabled(false);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < mForecastList.size(); i++) {
            float values = Float.parseFloat(mForecastList.get(i).getRain());
            entries.add(new Entry(i, values));
        }

        LineDataSet set;
        if (mRainChart.getData() != null) {
            mRainChart.getData().removeDataSet(mRainChart.getData().getDataSetByIndex(
                    mRainChart.getData().getDataSetCount() - 1));
            set = new LineDataSet(entries, "Rain");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#5677FC"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mRainChart.setData(data);
        } else {
            set = new LineDataSet(entries, "Rain");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#5677FC"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mRainChart.setData(data);
        }
        mRainChart.invalidate();
    }

    private void setSnowChart() {
        mSnowChart.setDescription("");
        mSnowChart.setDrawGridBackground(false);
        mSnowChart.setTouchEnabled(true);
        mSnowChart.setDragEnabled(true);
        mSnowChart.setMaxHighlightDistance(300);
        mSnowChart.setPinchZoom(true);
        mSnowChart.getLegend().setEnabled(false);

        formatDate();
        XAxis x = mSnowChart.getXAxis();
        x.setEnabled(true);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setValueFormatter(new XAxisValueFormatter(mDatesArray));

        YAxis yLeft = mSnowChart.getAxisLeft();
        yLeft.setEnabled(true);
        yLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yLeft.setDrawAxisLine(false);
        yLeft.setDrawGridLines(true);
        yLeft.enableGridDashedLine(5f, 10f, 0f);
        yLeft.setGridColor(Color.parseColor("#333333"));
        yLeft.setXOffset(15);
        yLeft.setValueFormatter(mYAxisFormatter);

        mSnowChart.getAxisRight().setEnabled(false);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < mForecastList.size(); i++) {
            float values = Float.parseFloat(mForecastList.get(i).getSnow());
            entries.add(new Entry(i, values));
        }

        LineDataSet set;
        if (mSnowChart.getData() != null) {
            mSnowChart.getData().removeDataSet(mSnowChart.getData().getDataSetByIndex(
                    mSnowChart.getData().getDataSetCount() - 1));
            set = new LineDataSet(entries, "Snow");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#009688"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mSnowChart.setData(data);
        } else {
            set = new LineDataSet(entries, "Snow");
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(2f);
            set.setValueTextSize(12f);
            set.setDrawValues(false);
            set.setColor(Color.parseColor("#009688"));
            set.setHighlightEnabled(false);
            set.setValueFormatter(mValueFormatter);

            LineData data = new LineData(set);
            mSnowChart.setData(data);
        }
        mSnowChart.invalidate();
    }

    private void formatDate() {
        SimpleDateFormat format = new SimpleDateFormat("EEE", Locale.getDefault());
        if (mForecastList != null) {
            int mSize = mForecastList.size();
            mDatesArray = new String[mSize];

            for (int i = 0; i < mSize; i++) {
                Date date = new Date(mForecastList.get(i).getDateTime() * 1000);
                String day = format.format(date);
                mDatesArray[i] = day;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_graphs, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (mConnectionDetector.isNetworkAvailableAndConnected()) {
                    getWeather();
                    setVisibleUpdating(true);
                } else {
                    Toast.makeText(this,
                                   R.string.connection_not_found,
                                   Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_toggle_values:
                toggleValues();
                return true;
            case R.id.action_toggle_yaxis:
                toggleYAxis();
                return true;
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleValues() {
        for (IDataSet set : mTemperatureChart.getData().getDataSets()) {
            set.setDrawValues(!set.isDrawValuesEnabled());
        }
        for (IDataSet set : mWindChart.getData().getDataSets()) {
            set.setDrawValues(!set.isDrawValuesEnabled());
        }
        for (IDataSet set : mRainChart.getData().getDataSets()) {
            set.setDrawValues(!set.isDrawValuesEnabled());
        }
        for (IDataSet set : mSnowChart.getData().getDataSets()) {
            set.setDrawValues(!set.isDrawValuesEnabled());
        }
        mTemperatureChart.invalidate();
        mWindChart.invalidate();
        mRainChart.invalidate();
        mSnowChart.invalidate();
    }

    private void toggleYAxis() {
        mTemperatureChart.getAxisLeft().setEnabled(!mTemperatureChart.getAxisLeft().isEnabled());
        mWindChart.getAxisLeft().setEnabled(!mWindChart.getAxisLeft().isEnabled());
        mRainChart.getAxisLeft().setEnabled(!mRainChart.getAxisLeft().isEnabled());
        mSnowChart.getAxisLeft().setEnabled(!mSnowChart.getAxisLeft().isEnabled());
        mTemperatureChart.invalidate();
        mWindChart.invalidate();
        mRainChart.invalidate();
        mSnowChart.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mForecastList = AppPreference.loadWeatherForecast(this);
        updateUI();
    }

    private void setVisibleUpdating(boolean visible) {
        if (visible) {
            mGetWeatherProgress.show();
        } else {
            mGetWeatherProgress.cancel();
        }
    }

    private void getWeather() {
        SharedPreferences pref = getSharedPreferences(Constants.APP_SETTINGS_NAME, 0);
        String latitude = pref.getString(Constants.APP_SETTINGS_LATITUDE, "51.51");
        String longitude = pref.getString(Constants.APP_SETTINGS_LONGITUDE, "-0.13");
        String locale = LanguageUtil.getLanguageName(PreferenceUtil.getLanguage(GraphsActivity.this));
        try {
            final URL url = getWeatherForecastUrl(Constants.WEATHER_FORECAST_ENDPOINT, latitude, longitude, "metric", locale);
            Handler mainHandler = new Handler(Looper.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    client.get(url.toString(), null, new AsyncHttpResponseHandler() {

                        @Override
                        public void onStart() {
                            // called before request is started
                        }

                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                            parseWeatherForecast(new String(response));
                            AppPreference.saveLastUpdateTimeMillis(GraphsActivity.this);
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                            appendLog(getBaseContext(), TAG, "onFailure:" + statusCode);
                        }

                        @Override
                        public void onRetry(int retryNo) {
                            // called when request is retried
                        }
                    });
                }
            };
            mainHandler.post(myRunnable);
        } catch (MalformedURLException mue) {
            appendLog(getBaseContext(), TAG, "MalformedURLException:" + mue);
            return;
        }
    }

    private void parseWeatherForecast(String data) {
        try {
            if (!mForecastList.isEmpty()) {
                mForecastList.clear();
            }

            JSONObject jsonObject = new JSONObject(data);
            JSONArray listArray = jsonObject.getJSONArray("list");

            int listArrayCount = listArray.length();
            for (int i = 0; i < listArrayCount; i++) {
                WeatherForecast weatherForecast = new WeatherForecast();
                JSONObject resultObject = listArray.getJSONObject(i);
                weatherForecast.setDateTime(resultObject.getLong("dt"));
                weatherForecast.setPressure(resultObject.getString("pressure"));
                weatherForecast.setHumidity(resultObject.getString("humidity"));
                weatherForecast.setWindSpeed(resultObject.getString("speed"));
                weatherForecast.setWindDegree(resultObject.getString("deg"));
                weatherForecast.setCloudiness(resultObject.getString("clouds"));
                if (resultObject.has("rain")) {
                    weatherForecast.setRain(resultObject.getString("rain"));
                } else {
                    weatherForecast.setRain("0");
                }
                if (resultObject.has("snow")) {
                    weatherForecast.setSnow(resultObject.getString("snow"));
                } else {
                    weatherForecast.setSnow("0");
                }
                JSONObject temperatureObject = resultObject.getJSONObject("temp");
                weatherForecast.setTemperatureMin(
                        Float.parseFloat(temperatureObject.getString("min")));
                weatherForecast.setTemperatureMax(
                        Float.parseFloat(temperatureObject.getString("max")));
                weatherForecast.setTemperatureMorning(
                        Float.parseFloat(temperatureObject.getString("morn")));
                weatherForecast.setTemperatureDay(
                        Float.parseFloat(temperatureObject.getString("day")));
                weatherForecast.setTemperatureEvening(
                        Float.parseFloat(temperatureObject.getString("eve")));
                weatherForecast.setTemperatureNight(
                        Float.parseFloat(temperatureObject.getString("night")));
                JSONArray weatherArray = resultObject.getJSONArray("weather");
                JSONObject weatherObject = weatherArray.getJSONObject(0);
                weatherForecast.setDescription(weatherObject.getString("description"));
                weatherForecast.setIcon(weatherObject.getString("icon"));

                mForecastList.add(weatherForecast);
                mHandler.sendEmptyMessage(Constants.PARSE_RESULT_SUCCESS);
            }
        } catch (JSONException e) {
            mHandler.sendEmptyMessage(Constants.TASK_RESULT_ERROR);
            e.printStackTrace();
        }
    }

    private void updateUI() {
        setTemperatureChart();
        setWindChart();
        setRainChart();
        setSnowChart();
    }
}

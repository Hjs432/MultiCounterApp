package com.example.multicounter.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.multicounter.model.Counter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地持久化工具：使用 SharedPreferences + JSON 序列化保存计数器列表。
 * 写入统一走 apply()（异步落盘），避免阻塞主线程造成卡顿。
 */
public class CounterStore {

    private static final String PREF_NAME = "multi_counter_store";
    private static final String KEY_COUNTERS = "counters";
    private static final String KEY_NEXT_INDEX = "next_index";

    /** 自动命名序号：计数器一 之后从「计数器2」开始 */
    private static final int DEFAULT_NEXT_INDEX = 2;

    private final SharedPreferences preferences;

    public CounterStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 读取本地保存的全部计数器。
     * 无数据或数据损坏时返回空列表，由调用方决定是否初始化默认计数器。
     */
    public List<Counter> loadCounters() {
        List<Counter> counters = new ArrayList<>();
        String json = preferences.getString(KEY_COUNTERS, null);
        if (json == null || json.isEmpty()) {
            return counters;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    counters.add(Counter.fromJson(item));
                }
            }
        } catch (JSONException e) {
            // 本地数据损坏：丢弃脏数据，由调用方重新初始化
            counters.clear();
        }
        return counters;
    }

    /** 保存全部计数器 */
    public void saveCounters(List<Counter> counters) {
        JSONArray array = new JSONArray();
        for (Counter counter : counters) {
            try {
                array.put(counter.toJson());
            } catch (JSONException ignored) {
                // 单个对象序列化失败不影响其余数据
            }
        }
        preferences.edit().putString(KEY_COUNTERS, array.toString()).apply();
    }

    /** 读取下一个自动命名序号 */
    public int getNextIndex() {
        return preferences.getInt(KEY_NEXT_INDEX, DEFAULT_NEXT_INDEX);
    }

    /** 保存下一个自动命名序号，保证新增计数器按顺序递增命名 */
    public void setNextIndex(int nextIndex) {
        preferences.edit().putInt(KEY_NEXT_INDEX, nextIndex).apply();
    }
}

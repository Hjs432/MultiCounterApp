package com.example.multicounter.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 计数器数据模型：唯一标识 + 名称 + 当前计数值。
 */
public class Counter {

    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_COUNT = "count";

    /** 唯一标识 */
    private long id;
    /** 计数器名称 */
    private String name;
    /** 当前计数值 */
    private int count;

    public Counter(long id, String name, int count) {
        this.id = id;
        this.name = name;
        this.count = count;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    /** 计数 +1 */
    public void increment() {
        count++;
    }

    /** 清零 */
    public void reset() {
        count = 0;
    }

    /** 序列化为 JSON，用于本地持久化 */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_ID, id);
        json.put(KEY_NAME, name);
        json.put(KEY_COUNT, count);
        return json;
    }

    /**
     * 反序列化。字段缺失时使用兜底默认值，避免历史脏数据导致崩溃。
     */
    public static Counter fromJson(JSONObject json) {
        long id = json.optLong(KEY_ID, System.currentTimeMillis());
        String name = json.optString(KEY_NAME, "");
        int count = json.optInt(KEY_COUNT, 0);
        return new Counter(id, name, count);
    }
}

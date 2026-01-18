package com.example.myapi.service;

import com.google.gson.JsonObject;

public interface TrafficService {
    JsonObject getTrafficInfo();
    void renewTrafficData();
}

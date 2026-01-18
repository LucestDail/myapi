package com.example.myapi.service;

import com.google.gson.JsonObject;

public interface EmergencyService {
    JsonObject getEmergencyInfo();
    void renewEmergencyData();
}

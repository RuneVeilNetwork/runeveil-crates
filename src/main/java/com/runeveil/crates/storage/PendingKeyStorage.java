package com.runeveil.crates.storage;

import java.util.LinkedHashMap;
import java.util.Map;

public class PendingKeyStorage {
    public Map<String, Map<String, Integer>> players = new LinkedHashMap<>();
}

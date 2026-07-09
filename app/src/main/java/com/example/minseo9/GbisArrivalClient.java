package com.example.minseo9;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class GbisArrivalClient {
    private static final String ENDPOINT =
            "https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalItemv2";

    Arrival fetchArrival(int stationId, int routeId) throws IOException {
        String query = "?serviceKey=" + encode(BuildConfig.GBIS_SERVICE_KEY)
                + "&format=json"
                + "&stationId=" + stationId
                + "&routeId=" + routeId;
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT + query).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
        InputStream stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readBody(stream);
        connection.disconnect();

        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("GBIS HTTP " + responseCode + ": " + body);
        }

        try {
            JSONObject response = new JSONObject(body).getJSONObject("response");
            JSONObject header = response.getJSONObject("msgHeader");
            int resultCode = header.optInt("resultCode", -1);
            if (resultCode != 0) {
                if (resultCode == 4) {
                    return Arrival.empty();
                }
                throw new IOException(header.optString("resultMessage", "GBIS result " + resultCode));
            }

            JSONObject msgBody = response.optJSONObject("msgBody");
            if (msgBody == null) {
                return Arrival.empty();
            }

            JSONObject item = msgBody.optJSONObject("busArrivalItem");
            if (item == null) {
                return Arrival.empty();
            }

            return new Arrival(
                    item.optInt("predictTime1", -1),
                    item.optString("predictTime2"),
                    item.optInt("locationNo1", -1),
                    item.optString("locationNo2"),
                    item.optString("stationNm1"),
                    item.optString("stationNm2"),
                    item.optString("routeDestName"),
                    item.optString("plateNo1"),
                    item.optString("plateNo2"),
                    item.optInt("remainSeatCnt1", -1),
                    item.optString("remainSeatCnt2")
            );
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("GBIS response parse failed", exception);
        }
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new AssertionError("UTF-8 is always supported", exception);
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    static final class Arrival {
        final int predictTime1;
        final String predictTime2;
        final int locationNo1;
        final String locationNo2;
        final String stationName1;
        final String stationName2;
        final String routeDestinationName;
        final String plateNo1;
        final String plateNo2;
        final int remainSeatCount1;
        final String remainSeatCount2;

        static Arrival empty() {
            return new Arrival(
                    -1,
                    "",
                    -1,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    -1,
                    ""
            );
        }

        Arrival(
                int predictTime1,
                String predictTime2,
                int locationNo1,
                String locationNo2,
                String stationName1,
                String stationName2,
                String routeDestinationName,
                String plateNo1,
                String plateNo2,
                int remainSeatCount1,
                String remainSeatCount2
        ) {
            this.predictTime1 = predictTime1;
            this.predictTime2 = predictTime2;
            this.locationNo1 = locationNo1;
            this.locationNo2 = locationNo2;
            this.stationName1 = stationName1;
            this.stationName2 = stationName2;
            this.routeDestinationName = routeDestinationName;
            this.plateNo1 = plateNo1;
            this.plateNo2 = plateNo2;
            this.remainSeatCount1 = remainSeatCount1;
            this.remainSeatCount2 = remainSeatCount2;
        }

        int predictTime(int vehicleIndex) {
            if (vehicleIndex == BusMonitorService.VEHICLE_SECOND) {
                return parseInt(predictTime2, -1);
            }
            return predictTime1;
        }

        int locationNo(int vehicleIndex) {
            if (vehicleIndex == BusMonitorService.VEHICLE_SECOND) {
                return parseInt(locationNo2, -1);
            }
            return locationNo1;
        }

        String stationName(int vehicleIndex) {
            if (vehicleIndex == BusMonitorService.VEHICLE_SECOND) {
                return stationName2;
            }
            return stationName1;
        }

        int remainSeatCount(int vehicleIndex) {
            if (vehicleIndex == BusMonitorService.VEHICLE_SECOND) {
                return parseInt(remainSeatCount2, -1);
            }
            return remainSeatCount1;
        }

        String plateNo(int vehicleIndex) {
            if (vehicleIndex == BusMonitorService.VEHICLE_SECOND) {
                return plateNo2;
            }
            return plateNo1;
        }

        private static int parseInt(String value, int fallback) {
            if (value == null || value.isEmpty()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }
}

package com.bada.badaback.safefacility.service;

import com.bada.badaback.safefacility.domain.Point;
import com.bada.badaback.safefacility.domain.SafeFacility;
import com.bada.badaback.safefacility.domain.SafeFacilityRepository;
import com.bada.badaback.safefacility.domain.Tile;
import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SafeFacilityService {
    private final SafeFacilityRepository safeFacilityRepository;

    /**
     * 경로찾기 알고리즘 구현
     * @param layerDepth
     * @param startX
     * @param startY
     * @param endX
     * @param endY
     * @return
     * @throws IOException
     */
    public String getCCTVs(int layerDepth, String startX, String startY, String endX, String endY) throws IOException {
        log.info("============getCCTVs Service start==============");
        Point start = new Point(Double.parseDouble(startY), Double.parseDouble(startX));
        Point end = new Point(Double.parseDouble(endY), Double.parseDouble(endX));

        //도착지와 출발지의 h3값 찾기
        H3Core h3 = H3Core.newInstance();
        int res = 11;

        String startHexAddr = h3.latLngToCellAddress(start.getLatitude(), start.getLongitude(), res);
        String endHexAddr = h3.latLngToCellAddress(end.getLatitude(), end.getLongitude(), res);

        Point mid = calculateMidpoint(start, end);

        //시작 ~ 도착 거리 계산
        double radius = distance(start.getLatitude(), start.getLongitude(), mid.getLatitude(), mid.getLongitude());

        List<SafeFacility> cctv = safeFacilityRepository.getCCTVs(String.valueOf(mid.getLatitude()), String.valueOf(mid.getLongitude()), radius);

        //police, guard 헥사곤 구하기 시작-----------------------------
        List<SafeFacility> guard = safeFacilityRepository.getGuard(String.valueOf(mid.getLatitude()), String.valueOf(mid.getLongitude()), radius);
        List<SafeFacility> police = safeFacilityRepository.getPolice(String.valueOf(mid.getLatitude()), String.valueOf(mid.getLongitude()), radius);

        Set<String> guardHexAddrs = new HashSet<>();
        Set<String> policeHexAddrs = new HashSet<>();
        Set<String> envHexAddrs = new HashSet<>();

        Iterator<SafeFacility> guardIte = guard.iterator();
        Iterator<SafeFacility> policeIte = police.iterator();

        while (guardIte.hasNext()) {
            SafeFacility next = guardIte.next();
            double Lat = Double.parseDouble(next.getFacilityLatitude());
            double Lng = Double.parseDouble(next.getFacilityLongitude());

            String guardHexAddr = h3.latLngToCellAddress(Lat, Lng, res);
            guardHexAddrs.add(guardHexAddr);
            envHexAddrs.add(guardHexAddr);
        }
        while (policeIte.hasNext()) {
            SafeFacility next = policeIte.next();
            double Lat = Double.parseDouble(next.getFacilityLatitude());
            double Lng = Double.parseDouble(next.getFacilityLongitude());

            String policeHexAddr = h3.latLngToCellAddress(Lat, Lng, res);
            policeHexAddrs.add(policeHexAddr);
            envHexAddrs.add(policeHexAddr);//전체 환경 변수에 추가
        }
        //police, guard 헥사곤 구하기 완료-----------------------------
        //1. cctv의 hexagon을 구한다 Tile로 저장해서 Tile.hexaddr 로 구분하는 자료구조 생성
        //전체 헥사곤 받아오고 이곳에 있는 hexagon만 쓰도록 조건문 추가

        List<String> BigHexagon = hexagonsAddress(start, end);

        Map<String, Tile> cctvHexagons = new HashMap<>();
        Iterator<SafeFacility> cctvIte = cctv.iterator();
        while (cctvIte.hasNext()) {
            SafeFacility next = cctvIte.next();
            double Lat = Double.parseDouble(next.getFacilityLatitude());
            double Lng = Double.parseDouble(next.getFacilityLongitude());

            String cctvHexagon = h3.latLngToCellAddress(Lat, Lng, res);

            if (BigHexagon.contains(cctvHexagon)) {
                Tile tile = new Tile(cctvHexagon);
                tile.setLeftDist(h3.gridDistance(endHexAddr, cctvHexagon));
                tile.setDist(h3.gridDistance(startHexAddr, cctvHexagon));
                if (cctvHexagons.containsKey(cctvHexagon)) {
                    Tile preTile = cctvHexagons.get(cctvHexagon);
                    preTile.cctvCount++;
                }
                cctvHexagons.put(cctvHexagon, tile);
            }

        }

        //2. cctv의 hexagon으로 주위를 탐색해서 주변에 guard와 police의 헥사곤이 있다면 Tile.envir 점수를 올린다.
        for (String key : cctvHexagons.keySet()) {
            //hexagon addr로 주위 헥사곤 리스트 가져오기
            List<String> neighbors = h3.gridDisk(key, 1);
            //neighbor에 envHexAddrs 에 포함되는 애가 있다면 그 타일의 환경 점수를 올려준다.
            for (String neighbor : neighbors) {
                if (envHexAddrs.contains(neighbor)) {
                    Tile tile = cctvHexagons.get(key);
                    tile.envir++;
                }
            }
        }

        //3. cctv를 starthexagon을 헥사곤 거리로 기준으로 5등분해서 거리별로 균등하게 cctv를 택할 수 있게 한다.
        //3-1. layer 5개를 만든다.
        //빈 layer 세팅
        List<List<Tile>> layer;
        layer = makeLayer(layerDepth, startHexAddr, endHexAddr, cctvHexagons);
//        log.info("============cctv 전체 개수==========");
//        log.info("개수: {}",cctvHexagons.size());
//        log.info("============layer 결과==========");
//        log.info("layer size: {}",layer.size());
//        for (int i = 0; i < layer.size(); i++) {
//            log.info("{}번 layer: {}", i, layer.get(i).toString());
//        }

        //4. 각 layer마다 현재의 최선을 선택해서 경유지를 선택하도록 짠다. - dfs 완탐
        //4-1. 다음 layer에 cctvHexagon이 없으면 그 다음 layer 고려해서 판단
        List<String> pass = new ArrayList<>();
        pass = PassCCTV(layer, 1, layer.get(0).get(0), pass);

        //5. 결정된 hexagon에 있는 cctv의 좌표 passList를 만든다. (만약 여러개의 cctv가 있다면 랜덤으로 도착지에 보낸다.)
        List<Point> passList = hexagonsCoordinates(pass);
//        log.info("=============경유지 목록==========");
//        log.info(passList.toString());

        StringBuilder sb = new StringBuilder();
        Iterator<Point> passIte = passList.iterator();
        while (passIte.hasNext()) {
            Point next = passIte.next();
            double Lat = next.getLatitude();
            double Lng = next.getLongitude();
            sb.append(Lng + ", " + Lat);

            if (passIte.hasNext()) {
                sb.append("_");
            }
        }

        log.info("===============getCCTVs Service end===============");
        return sb.toString();
    }

    private List<List<Tile>> makeLayer(int count, String startHexAddr, String endHexAddr, Map<String, Tile> cctvHexagons) throws IOException {
        List<List<Tile>> layer = new ArrayList<>();
        H3Core h3 = H3Core.newInstance();

        long stoeDistance = h3.gridDistance(startHexAddr, endHexAddr);

        for (int i = 0; i <= count; i++) {
            layer.add(new ArrayList<>());
        }
        Tile tile = new Tile(startHexAddr);
        tile.setLeftDist(stoeDistance);
//        log.info("=============출발지부터 도착지까지의 거리===============");
//        log.info("stoeDistance: {}",stoeDistance);

        layer.get(0).add(tile);
        layer.get(count).add(new Tile(endHexAddr));
        //전체 탐색을 돌리고 출발지로부터의 거기를 구하고 전체거리에서 1/n 각 레이어마다 cctv를 넣는다

        //cctv를 돌면서 알맞은 layer에 넣는다. 만약
//        log.info("======cctv 레이어 하나 길이=======");
//        log.info("count: {}",count-1);
//        log.info("cctv: {}", stoeDistance/(count-1));
        for (String key : cctvHexagons.keySet()) {
            int layerNum = getLayerNum(stoeDistance,cctvHexagons.get(key).dist,count-2);
//            log.info("============cctv Dist 비교===========");
//            log.info("{}위치의 cctv dist {} | layer: {}",key,cctvHexagons.get(key).dist,layerNum);
            layer.get(layerNum).add(cctvHexagons.get(key));
        }
        return layer;
    }

    public int getLayerNum(long stoeDistance, long dist, int layerCount){
        double result = dist / (stoeDistance/layerCount);
        if(dist%(stoeDistance/layerCount)==0){
            return (int) result;
        }else {
            return (int) result+1;
        }
    }


    //현재의 최선 알고리즘
    //현재 노드에서 다음 레이어에 있는 노드로 가는 것 중 거리가 먼 것을 선택한다

    /**
     * layer를 까서 만든다. 현재 고려할 레이어 now와 now-1에서 선택했던 index
     * @param layer
     * @param now
     * @param beforeTile
     * @param route
     * @return
     * @throws IOException
     */
    private List<String> PassCCTV(List<List<Tile>> layer, int now, Tile beforeTile, List<String> route) throws IOException {
        // now는 현재 레이어의 숫자이다.
        // 만약 layer가 4가 된다면 5개의 레이어까지 왔으므로 도착지까지 보낸다.
        // 만약 첫번째 레이어면 출발과 비교한다.

        String beforeHexAddr = beforeTile.getHexAddr();

        H3Core h3 = H3Core.newInstance();
        if (now >= layer.size() - 2) {
            //endlayer에서 hexaddr을 뽑아서 넣는다
            return route;
        }
        //1번째 layer부터 고려해서 beforeHexAddr를 기준으로 for문을 돌려서 가장 거리값이 작은 것을 고른다
        long min = Long.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < layer.get(now).size(); i++) {
            String nowHexAddr = layer.get(now).get(i).getHexAddr();
            long dist = h3.gridDistance(nowHexAddr, beforeHexAddr);
            if (dist < min) {
                minIndex = i;
                min = dist;
            }
        }
        //아직 minIndex가 -1이면 레이어에 값이 없었다는 것이니 추가하지 않고 다음 레이어로 넘어가는 함수를 호출한다.
        if (minIndex == -1) {
            return PassCCTV(layer, now + 1, beforeTile, route);
        } else {
            //현재 넣을 타일의 도착지와의 거리가
            long nowLeftDist = layer.get(now).get(minIndex).getLeftDist();
            //이전의 거리보다 가까워졌으면 add
            long beforeLeftDist = beforeTile.getLeftDist();
            if (nowLeftDist < beforeLeftDist) {
                route.add(layer.get(now).get(minIndex).getHexAddr());
            }
            return PassCCTV(layer, now + 1, layer.get(now).get(minIndex), route);
        }
    }//PassCCTV

    public Point calculateMidpoint(Point start, Point end) {
        // 위도와 경도의 평균값 계산
        double midLat = (start.getLatitude() + end.getLatitude()) / 2;
        double midLong = (start.getLongitude() + end.getLongitude()) / 2;

        // 중간 좌표 반환
        return new Point(midLat, midLong);
    }

    public double distance(double lat1, double lon1, double lat2, double lon2) {
        int R = 6371; // 지구 반지름 (단위: km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double[] calc_offsets(double distance, double lat) {
        double[] offsets = new double[2];
        offsets[0] = 180 * distance / 6371 / 1000 / Math.PI * 1000; // lat
        offsets[1] = Math.abs(360 * Math.asin(Math.sin(distance / 6371 / 2 / 1000) / Math.cos(lat * Math.PI / 180)) / Math.PI) * 1000; // lon
        return offsets;
    }

    private double[] coordinate_after_rotation(double lat, double lon, double degree, double[] offsets) {
        double[] coordinate = new double[2];
        coordinate[0] = lat + Math.sin(Math.toRadians(degree)) * offsets[0];
        coordinate[1] = lon + Math.cos(Math.toRadians(degree)) * offsets[1];
        return coordinate;
    }

    // [8b30e3634d61fff, 8b30e3634c20fff, 8b30e3634c12fff, ... ]
    private List<String> hexagonsAddress(Point start, Point mid) throws IOException {
        double radius = distance(start.getLatitude(), start.getLongitude(), mid.getLatitude(), mid.getLongitude());
        double[] offsets = calc_offsets(radius, mid.getLatitude());
        // 헥사곤 경계 좌표 리스트
        List<LatLng> polygon = new ArrayList<>();
        for (int d = 0; d <= 360; d += 45) {
            double[] coordinate = coordinate_after_rotation(mid.getLatitude(), mid.getLongitude(), d, offsets);
            polygon.add(new LatLng(coordinate[0], coordinate[1])); // lat, lon
        }

        H3Core h3 = H3Core.newInstance();
        int res = 11;

        return h3.polygonToCellAddresses(polygon, null, res);
    }

    // [Point(longitude=127.38555434963529, latitude=36.41950933776037)...]
    private List<Point> hexagonsCoordinates(List<String> hexagons) throws IOException {
        H3Core h3 = H3Core.newInstance();

        List<Point> list = new ArrayList<>();

        for (String hexagon : hexagons) {
            LatLng latLng = h3.cellToLatLng(hexagon);
            list.add(new Point(latLng.lat, latLng.lng));
        }
        return list;
    }
}

package com.bada.badaback.safefacility.service;

import com.bada.badaback.common.ServiceTest;
import com.bada.badaback.feature.SafeFacilityFixture;
import com.bada.badaback.safefacility.domain.SafeFacility;
import com.bada.badaback.safefacility.domain.SafeFacilityRepository;
import com.bada.badaback.safefacility.domain.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SafeFacilityService 테스트")
class SafeFacilityServiceTest extends ServiceTest {
    @Autowired
    private SafeFacilityService safeFacilityService;
    @Autowired
    private SafeFacilityRepository safeFacilityRepository;

    @Test
    @DisplayName("getCCTV에서 출발지 도착지의 헥사곤 주소 테스트")
    void getCCTVs() throws IOException {
        //given
        SafeFacility cctv1 = SafeFacility.createSafeFaciclity(SafeFacilityFixture.CCTV1.getFacilityLatitude(), SafeFacilityFixture.CCTV1.getFacilityLongitude(), Type.CCTV);
        safeFacilityRepository.save(cctv1);
        SafeFacility cctv2 = SafeFacility.createSafeFaciclity(SafeFacilityFixture.CCTV2.getFacilityLatitude(), SafeFacilityFixture.CCTV2.getFacilityLongitude(), Type.CCTV);
        safeFacilityRepository.save(cctv2);
        SafeFacility cctv3 = SafeFacility.createSafeFaciclity(SafeFacilityFixture.CCTV3.getFacilityLatitude(), SafeFacilityFixture.CCTV3.getFacilityLongitude(), Type.CCTV);
        safeFacilityRepository.save(cctv3);
        SafeFacility cctv4 = SafeFacility.createSafeFaciclity(SafeFacilityFixture.CCTV4.getFacilityLatitude(), SafeFacilityFixture.CCTV4.getFacilityLongitude(), Type.CCTV);
        safeFacilityRepository.save(cctv4);
        SafeFacility cctv5 = SafeFacility.createSafeFaciclity(SafeFacilityFixture.CCTV5.getFacilityLatitude(), SafeFacilityFixture.CCTV5.getFacilityLongitude(), Type.CCTV);
        safeFacilityRepository.save(cctv5);

        //when
//        String responseDto = safeFacilityService.getCCTVs(7, SafeFacilityFixture.START.getFacilityLongitude(), SafeFacilityFixture.START.getFacilityLatitude(),
//                SafeFacilityFixture.END.getFacilityLongitude(), SafeFacilityFixture.END.getFacilityLatitude());

        String responseDto = safeFacilityService.getCCTVs(7, "127.376824071213", "36.3591472142881",
                "127.377388462562", "36.3671226176298");

        //then
//        assertEquals("127.38673037076735, 36.423949949489696_127.38422434421484, 36.42159507519073", responseDto);
//        assertEquals("127.38673037076735, 36.423949949489696", responseDto);
        assertEquals("", responseDto);
    }
}
package com.gml.autoblocker;

/**
 * 위젯 목록의 3×1 항목. 2×1 과 상태, 스타일 저장값, 탭 동작을 함께 쓰고 오른쪽에 Play 프로텍트 바로가기를 더한다.
 * 레이아웃은 위젯 폭이 아니라 이 provider 로 고른다(AutoBlockerWidget.render).
 */
public class AutoBlockerWideWidget extends AutoBlockerWidget {
    @Override
    boolean wide() {
        return true;
    }
}

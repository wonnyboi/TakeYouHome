import 'dart:convert';

import 'package:bada/models/search_history.dart';
import 'package:bada/models/search_results.dart';
import 'package:bada/screens/main/my_place/screen/search_map_screen.dart';
import 'package:bada/widgets/appbar.dart';
import 'package:bada/widgets/screensize.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class MapSearch extends StatefulWidget {
  final VoidCallback onDataUpdate;
  const MapSearch({
    super.key,
    required this.onDataUpdate,
  });

  @override
  State<MapSearch> createState() => _MapSearchState();
}

class _MapSearchState extends State<MapSearch>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  final TextEditingController _controller = TextEditingController();
  Future<List<SearchResultItem>>? _searchResult;
  List<SearchHistory> _searchHistory = [];
  bool _isSearching = false;

  Future<List<SearchResultItem>> fetchSearchResults(String keyword) async {
    var url = Uri.parse(
      'https://dapi.kakao.com/v2/local/search/keyword.json?query=$keyword',
    );

    var response = await http.get(
      url,
      headers: {'Authorization': 'KakaoAK be8a38ff76c199cc88b459e8c29957be'},
    );

    if (response.statusCode == 200) {
      setState(
        () => _isSearching = true,
      );
      List<dynamic> jsonList = json.decode(response.body)['documents'];
      List<SearchResultItem> items = jsonList
          .map((jsonItem) => SearchResultItem.fromJson(jsonItem))
          .toList();
      return items;
    } else {
      throw Exception('Failed to load search results');
    }
  }

  Future<void> _loadSearchHistory() async {
    final prefs = await SharedPreferences.getInstance();
    final List<String>? jsonStringList = prefs.getStringList('searchHistory');
    if (jsonStringList != null) {
      // JSON 문자열을 SearchHistory 객체로 변환
      final List<SearchHistory> loadedSearchHistory =
          jsonStringList.map((jsonString) {
        final decoded = jsonDecode(jsonString);
        return SearchHistory.fromJson(decoded);
      }).toList();

      setState(() {
        _searchHistory = loadedSearchHistory;
      });
    }
  }

  Future<void> _saveSearchKeyword(String keyword) async {
    final prefs = await SharedPreferences.getInstance();
    final searchHistory =
        SearchHistory(keyword: keyword, timestamp: DateTime.now());
    _searchHistory.insert(0, searchHistory); // 새로운 검색어를 맨 앞에 추가

    // 검색 기록을 JSON 문자열 리스트로 변환
    List<String> jsonStringList =
        _searchHistory.map((history) => jsonEncode(history.toJson())).toList();
    prefs.setStringList('searchHistory', jsonStringList);
  }

  Future<void> _resetSearchHistory(String keyword) async {
    final prefs = await SharedPreferences.getInstance();

    // 특정 키워드와 일치하는 모든 기록을 제거합니다.
    _searchHistory.removeWhere((item) => item.keyword == keyword);

    // 새로운 검색 기록을 생성하고 맨 앞에 추가합니다.
    final searchHistory =
        SearchHistory(keyword: keyword, timestamp: DateTime.now());
    _searchHistory.insert(0, searchHistory);

    // 변경된 검색 기록을 JSON 문자열 리스트로 변환합니다.
    List<String> jsonStringList =
        _searchHistory.map((history) => jsonEncode(history.toJson())).toList();

    // 변경된 검색 기록을 SharedPreferences에 저장합니다.
    await prefs.setStringList('searchHistory', jsonStringList);

    // 검색 기록을 다시 로드합니다.
    _loadSearchHistory();
  }

  late final AnimationController _lottieController;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this); // Observer 등록
    _lottieController = AnimationController(vsync: this);
    _loadSearchHistory();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this); // Observer를 해제합니다.
    _lottieController.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    // 앱이 다시 활성화될 때 애니메이션을 재시작합니다.
    if (state == AppLifecycleState.resumed) {
      _lottieController.forward();
    }
  }

  @override
  Widget build(BuildContext context) {
    final deviceWidth = MediaQuery.of(context).size.width;
    final deviceHeight = MediaQuery.of(context).size.height;

    return Scaffold(
      appBar: const CustomAppBar(title: '검색'),
      body: Container(
        color: Colors.white,
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 0),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              child: TextField(
                controller: _controller,
                decoration: InputDecoration(
                  contentPadding: const EdgeInsets.fromLTRB(20, 15, 15, 15),
                  filled: true,
                  fillColor: Colors.white,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: const BorderSide(
                      color: Colors.black,
                      width: 0.1,
                    ),
                  ),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.search),
                    onPressed: () {
                      // controller.text로 검색
                      setState(() {
                        _searchResult = fetchSearchResults(_controller.text);
                      });
                    },
                  ),
                ),
                onSubmitted: (value) => setState(() {
                  _searchResult = fetchSearchResults(value);
                }),
              ),
            ),
            SizedBox(height: UIhelper.scaleHeight(context) * 10),
            Container(
              padding: EdgeInsets.symmetric(
                horizontal: UIhelper.scaleWidth(context) * 15,
              ),
              child: _isSearching
                  ? const Row(
                      mainAxisAlignment: MainAxisAlignment.start,
                      children: [
                        Text(
                          '검색 결과',
                          style: TextStyle(color: Colors.black26),
                        ),
                      ],
                    )
                  : const Row(
                      mainAxisAlignment: MainAxisAlignment.start,
                      children: [
                        Text(
                          '검색 기록',
                          style: TextStyle(color: Colors.black26),
                        ),
                      ],
                    ),
            ),
            // 검색 결과를 보여주는 부분
            Expanded(
              child: FutureBuilder<List<SearchResultItem>>(
                future: _searchResult,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    // 데이터 로딩 중일 때
                    return const Center(child: CircularProgressIndicator());
                  } else if (snapshot.hasData) {
                    // 데이터가 성공적으로 로드되었을 때
                    return ListView.builder(
                      itemCount: snapshot.data!.length,
                      itemBuilder: (context, index) {
                        SearchResultItem item = snapshot.data![index];
                        return ListTile(
                          onTap: () {
                            _resetSearchHistory(item.placeName);
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => SearchMapScreen(
                                  item: item,
                                  keyword: _controller.text,
                                  onDataUpdate: widget.onDataUpdate,
                                ),
                              ),
                            );
                          },
                          title: Text(
                            item.placeName,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            item.addressName,
                            overflow: TextOverflow.ellipsis,
                          ),
                        );
                      },
                    );
                  } else {
                    // 데이터가 없을 때
                    return ListView.builder(
                      itemCount: _searchHistory.length,
                      itemBuilder: (context, index) {
                        final keyword = _searchHistory[index].keyword;
                        final date = _searchHistory[index].timestamp;
                        final formattedDate =
                            '${date.month.toString().padLeft(2, '0')}.${date.day.toString().padLeft(2, '0')}';
                        return ListTile(
                          title: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              SizedBox(
                                width: UIhelper.scaleWidth(context) * 250,
                                child: Text(
                                  keyword,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              Text(
                                formattedDate,
                                style: const TextStyle(color: Colors.black26),
                              ),
                            ],
                          ),
                          onTap: () {
                            _controller.text = keyword;
                            setState(() {
                              _searchResult =
                                  fetchSearchResults(_controller.text);
                            });
                          },
                        );
                      },
                    );
                  }
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

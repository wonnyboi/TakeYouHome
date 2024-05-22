import 'package:bada_kids_front/model/buttons.dart';
import 'package:bada_kids_front/model/place.dart';
import 'package:bada_kids_front/model/screen_size.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class DestinationSelectScreen extends StatefulWidget {
  const DestinationSelectScreen({super.key});

  @override
  State<DestinationSelectScreen> createState() =>
      _DestinationSelectScreenState();
}

// TODO :
class _DestinationSelectScreenState extends State<DestinationSelectScreen> {
  Future<List<Place>>? myPlaces;

  String accessToken = '';
  FlutterSecureStorage secureStorage = const FlutterSecureStorage();

  Future<void> _loadAccessToken() async {
    accessToken = (await secureStorage.read(key: 'accessToken'))!;
    debugPrint('accessToken: $accessToken');
  }

  @override
  void initState() {
    super.initState();
    _loadAccessToken().then((_) {
      setState(() {
        myPlaces = MyPlaceData.loadPlaces(accessToken);
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    double deviceHeight = UIhelper.deviceHeight(context);
    double deviceWidth = UIhelper.deviceWidth(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '어디로 가시나요?',
          style: TextStyle(fontFamily: 'Pretendard-Bold.otf'),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios), // 원하는 아이콘으로 변경
          onPressed: () {
            Navigator.pop(context); // 현재 화면을 닫고 이전 화면으로 돌아가기
          },
        ),
      ),
      body: Container(
        color: Colors.white,
        child: Column(
          children: [
            Expanded(
              child: FutureBuilder<List<Place>>(
                future: myPlaces,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.done) {
                    if (snapshot.hasData && snapshot.data!.isNotEmpty) {
                      var places = snapshot.data!;
                      return ListView.builder(
                        itemCount: places.length,
                        itemBuilder: (context, index) {
                          return Column(
                            children: [
                              MyPlaceButton(
                                placeName: places[index].placeName,
                                placeLatitude: places[index].placeLatitude,
                                placeLongitude: places[index].placeLongitude,
                                icon: places[index].icon,
                                addressName: places[index].addressName,
                                myPlaceId: places[index].myPlaceId,
                              ),
                              SizedBox(
                                height: UIhelper.scaleHeight(context) * 5,
                              ),
                            ],
                          );
                        },
                      );
                    } else if (snapshot.hasError) {
                      return Center(child: Text('Error: ${snapshot.error}'));
                    } else {
                      return const Center(child: Text('아직 등록된 장소가 없습니다.'));
                    }
                  } else {
                    return const Center(child: CircularProgressIndicator());
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

# Flutter Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the `carpool-mobile` Flutter project with auth (Telegram Login Widget via WebView), JWT storage, Riverpod state, go_router navigation, and all core freezed DTOs — everything downstream feature work builds on.

**Architecture:** Feature-first layout under `lib/`. `core/` owns cross-cutting concerns (API client, auth, router, models). Each feature owns its own screens and providers. The login flow uses `flutter_inappwebview` to render the existing Next.js `/login` page, intercepts the `carpool://auth` redirect scheme, extracts the JWT, stores it in `flutter_secure_storage`, and navigates to the home shell.

**Tech Stack:** Flutter 3.x, Dart 3.x, flutter_riverpod 2.x + riverpod_annotation, go_router 14.x, Dio 5.x, flutter_secure_storage 9.x, flutter_inappwebview 6.x, freezed 2.x + json_serializable 6.x, build_runner, Material Design 3.

---

## File Map

| File | Responsibility |
|------|---------------|
| `carpool-mobile/pubspec.yaml` | All dependencies + dev_dependencies |
| `carpool-mobile/lib/main.dart` | Entry point — `runApp(ProviderScope(child: CarpoolApp()))` |
| `carpool-mobile/lib/app.dart` | `CarpoolApp` — `MaterialApp.router(routerConfig: router)` |
| `carpool-mobile/lib/core/config/app_config.dart` | `baseUrl`, `loginUrl` constants |
| `carpool-mobile/lib/core/api/api_exception.dart` | Typed API error model |
| `carpool-mobile/lib/core/api/dio_client.dart` | Dio instance, JWT Bearer interceptor, 401 logout redirect |
| `carpool-mobile/lib/core/auth/token_storage.dart` | `flutter_secure_storage` wrapper: read/write/delete JWT |
| `carpool-mobile/lib/core/auth/auth_repository.dart` | `loginWithTelegram(payload)`, `getMe()`, `logout()` |
| `carpool-mobile/lib/core/auth/auth_provider.dart` | Riverpod `AsyncNotifier<AuthUser?>` — auth state |
| `carpool-mobile/lib/core/models/enums.dart` | `RideDirection`, `RideStatus`, `BookingStatus`, `UserRole` |
| `carpool-mobile/lib/core/models/user.dart` | `AuthUser`, `UserSummary` |
| `carpool-mobile/lib/core/models/ride.dart` | `Ride`, `RideSummary` |
| `carpool-mobile/lib/core/models/booking.dart` | `Booking`, `BookingSummary` |
| `carpool-mobile/lib/core/models/hub.dart` | `Hub` |
| `carpool-mobile/lib/core/models/rating.dart` | `Rating` |
| `carpool-mobile/lib/core/models/vehicle.dart` | `Vehicle` |
| `carpool-mobile/lib/core/models/paged_response.dart` | Generic `PagedResponse<T>` |
| `carpool-mobile/lib/core/router/router.dart` | GoRouterNotifier (ChangeNotifier), stable GoRouter, auth redirect guard |
| `carpool-mobile/lib/features/auth/login_screen.dart` | WebView rendering Telegram Login Widget, `carpool://auth` intercept |
| `carpool-mobile/lib/features/splash/splash_screen.dart` | Loading spinner shown during AsyncLoading auth state |
| `carpool-mobile/lib/features/home/home_screen.dart` | Bottom nav shell — 3 tabs (Passenger) or 4 tabs (Driver/Both) |
| `carpool-mobile/lib/features/rides/rides_stub_screen.dart` | Placeholder for rides tab |
| `carpool-mobile/lib/features/bookings/bookings_stub_screen.dart` | Placeholder for bookings tab |
| `carpool-mobile/lib/features/profile/profile_stub_screen.dart` | Placeholder for profile tab |
| `carpool-mobile/lib/features/my_rides/my_rides_stub_screen.dart` | Placeholder for driver My Rides tab |

---

## Task 1: Flutter project scaffold + pubspec.yaml

**Files:**
- Create: `carpool-mobile/pubspec.yaml`
- Create: `carpool-mobile/android/app/build.gradle` (minSdk override)

- [ ] **Step 1: Create the Flutter project**

From a terminal in the repo root:

```bash
flutter create --org com.carpool --project-name carpool_mobile --platforms android carpool-mobile
```

Expected: `carpool-mobile/` directory created with standard Flutter scaffold.

- [ ] **Step 2: Replace pubspec.yaml with full dependency set**

Replace `carpool-mobile/pubspec.yaml` with:

```yaml
name: carpool_mobile
description: Car-E-Pool mobile app — Android-first, both Driver and Passenger roles.
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter
  flutter_riverpod: ^2.5.1
  riverpod_annotation: ^2.3.5
  go_router: ^14.2.0
  dio: ^5.4.3
  flutter_secure_storage: ^9.2.2
  flutter_inappwebview: ^6.0.0
  freezed_annotation: ^2.4.4
  json_annotation: ^4.9.0
  cached_network_image: ^3.3.1

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^3.0.0
  build_runner: ^2.4.9
  freezed: ^2.5.2
  json_serializable: ^6.8.0
  riverpod_generator: ^2.4.0
  custom_lint: ^0.6.4
  riverpod_lint: ^2.3.10
  mockito: ^5.4.4
  http_mock_adapter: ^0.6.1

flutter:
  uses-material-design: true
```

- [ ] **Step 3: Set Android compileSdkVersion to 34 and minSdk to 21**

> `flutter_inappwebview` 6.x requires `compileSdkVersion 34`. Without this, the build fails with a manifest merger error.

In `carpool-mobile/android/app/build.gradle`, find the `defaultConfig` block and set:

```groovy
android {
    compileSdkVersion 34

    defaultConfig {
        applicationId "com.carpool.carpool_mobile"
        minSdkVersion 21
        targetSdkVersion 34
        versionCode flutterVersionCode.toInteger()
        versionName flutterVersionName
    }
}
```

- [ ] **Step 4: Add flutter_inappwebview Android permission**

In `carpool-mobile/android/app/src/main/AndroidManifest.xml`, add inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

(Flutter projects usually have this already; verify it is present.)

- [ ] **Step 5: Install dependencies**

```bash
cd carpool-mobile && flutter pub get
```

Expected: `Resolving dependencies... Got dependencies!` with no version conflicts.

- [ ] **Step 6: Commit**

```
git add carpool-mobile/
git commit -m "chore: scaffold carpool-mobile Flutter project"
```

---

## Task 2: App config

**Files:**
- Create: `carpool-mobile/lib/core/config/app_config.dart`

- [ ] **Step 1: Create `app_config.dart`**

```dart
import 'package:flutter/foundation.dart';

class AppConfig {
  static const String baseUrl = 'https://api.car-e-pool.com/api/v1';
  static const String loginUrl = 'https://car-e-pool.com/login';
  static const String authRedirectScheme = 'carpool';

  // Dev-only: set to a valid JWT for smoke testing without the Next.js redirect.
  // Must remain null in production. Guard: only read when kDebugMode is true.
  static const String? devBypassToken = null;
}
```

> Local dev: swap `baseUrl` to `'http://10.0.2.2:8080/api/v1'` (Android emulator loopback).
>
> **Cross-team dependency:** `LoginScreen` requires the Next.js `/login` page to redirect to `carpool://auth?token=<jwt>` after `POST /api/v1/auth/telegram` succeeds. Until that is deployed, use `AppConfig.devBypassToken` for Day 1 testing: set it to a valid JWT, then call `ref.read(tokenStorageProvider).write(AppConfig.devBypassToken!)` in `main()` before `runApp()` when `kDebugMode` is true. Remove before production.

- [ ] **Step 2: Commit**

```
git add carpool-mobile/lib/core/config/
git commit -m "chore: add AppConfig with base URL and auth redirect scheme"
```

---

## Task 3: Core freezed models

**Files:**
- Create: `carpool-mobile/lib/core/models/enums.dart`
- Create: `carpool-mobile/lib/core/models/user.dart`
- Create: `carpool-mobile/lib/core/models/ride.dart`
- Create: `carpool-mobile/lib/core/models/booking.dart`
- Create: `carpool-mobile/lib/core/models/hub.dart`
- Create: `carpool-mobile/lib/core/models/rating.dart`
- Create: `carpool-mobile/lib/core/models/vehicle.dart`
- Create: `carpool-mobile/lib/core/models/paged_response.dart`

- [ ] **Step 1: Create `enums.dart`**

```dart
import 'package:json_annotation/json_annotation.dart';

enum RideDirection {
  @JsonValue('HOME_TO_WORK') homeToWork,
  @JsonValue('WORK_TO_HOME') workToHome,
}

enum RideStatus {
  @JsonValue('DRAFT') draft,
  @JsonValue('ACTIVE') active,
  @JsonValue('DEPARTED') departed,
  @JsonValue('COMPLETED') completed,
  @JsonValue('CANCELLED') cancelled,
}

enum BookingStatus {
  @JsonValue('PENDING') pending,
  @JsonValue('CONFIRMED') confirmed,
  @JsonValue('DECLINED') declined,
  @JsonValue('CANCELLED') cancelled,
  @JsonValue('COMPLETED') completed,
}

enum UserRole {
  @JsonValue('DRIVER') driver,
  @JsonValue('PASSENGER') passenger,
  @JsonValue('BOTH') both,
}
```

- [ ] **Step 2: Create `user.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';
import 'enums.dart';

part 'user.freezed.dart';
part 'user.g.dart';

@freezed
class AuthUser with _$AuthUser {
  const factory AuthUser({
    required int id,
    required String firstName,
    String? lastName,
    String? username,
    String? photoUrl,
    required UserRole role,
    required bool termsAccepted,
    @Default(0) int totalRidesAsDriver,
    @Default(0) int totalRidesAsPassenger,
    @Default(0.0) double averageRatingAsDriver,
    @Default(0.0) double averageRatingAsPassenger,
  }) = _AuthUser;

  factory AuthUser.fromJson(Map<String, dynamic> json) =>
      _$AuthUserFromJson(json);
}

@freezed
class UserSummary with _$UserSummary {
  const factory UserSummary({
    required int id,
    required String firstName,
    String? lastName,
    String? username,
    String? photoUrl,
    required UserRole role,
    @Default(0.0) double averageRatingAsDriver,
    @Default(0) int totalRidesAsDriver,
  }) = _UserSummary;

  factory UserSummary.fromJson(Map<String, dynamic> json) =>
      _$UserSummaryFromJson(json);
}
```

- [ ] **Step 3: Create `hub.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

part 'hub.freezed.dart';
part 'hub.g.dart';

@freezed
class Hub with _$Hub {
  const factory Hub({
    required int id,
    required String name,
    String? address,
  }) = _Hub;

  factory Hub.fromJson(Map<String, dynamic> json) => _$HubFromJson(json);
}
```

- [ ] **Step 4: Create `vehicle.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

part 'vehicle.freezed.dart';
part 'vehicle.g.dart';

@freezed
class Vehicle with _$Vehicle {
  const factory Vehicle({
    required int id,
    required String make,
    required String model,
    required String color,
    required String plateNumber,
  }) = _Vehicle;

  factory Vehicle.fromJson(Map<String, dynamic> json) =>
      _$VehicleFromJson(json);
}
```

- [ ] **Step 5: Create `ride.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';
import 'enums.dart';
import 'hub.dart';
import 'user.dart';
import 'vehicle.dart';

part 'ride.freezed.dart';
part 'ride.g.dart';

@freezed
class Ride with _$Ride {
  const factory Ride({
    required int id,
    required UserSummary driver,
    required RideDirection direction,
    required RideStatus status,
    required Hub originHub,
    required Hub destinationHub,
    required String departureTime,
    required int totalSeats,
    required int availableSeats,
    required double gasContribution,
    String? note,
    Vehicle? vehicle,
  }) = _Ride;

  factory Ride.fromJson(Map<String, dynamic> json) => _$RideFromJson(json);
}
```

- [ ] **Step 6: Create `booking.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';
import 'enums.dart';
import 'ride.dart';
import 'user.dart';

part 'booking.freezed.dart';
part 'booking.g.dart';

@freezed
class Booking with _$Booking {
  const factory Booking({
    required int id,
    required Ride ride,
    required UserSummary passenger,
    required BookingStatus status,
    String? passengerNote,
    String? declineReason,
    String? createdAt,
  }) = _Booking;

  factory Booking.fromJson(Map<String, dynamic> json) =>
      _$BookingFromJson(json);
}
```

- [ ] **Step 7: Create `rating.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';
import 'user.dart';

part 'rating.freezed.dart';
part 'rating.g.dart';

@freezed
class Rating with _$Rating {
  const factory Rating({
    required int id,
    required UserSummary rater,
    required int stars,
    String? comment,
    required String createdAt,
  }) = _Rating;

  factory Rating.fromJson(Map<String, dynamic> json) => _$RatingFromJson(json);
}
```

- [ ] **Step 8: Create `paged_response.dart`**

```dart
class PagedResponse<T> {
  final List<T> content;
  final int totalElements;
  final int totalPages;
  final int number;
  final bool last;

  const PagedResponse({
    required this.content,
    required this.totalElements,
    required this.totalPages,
    required this.number,
    required this.last,
  });

  factory PagedResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object?) fromJsonT,
  ) {
    return PagedResponse(
      content: (json['content'] as List).map(fromJsonT).toList(),
      totalElements: json['totalElements'] as int,
      totalPages: json['totalPages'] as int,
      number: json['number'] as int,
      last: json['last'] as bool,
    );
  }
}
```

- [ ] **Step 9: Run code generation**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

Expected: generates `*.freezed.dart` and `*.g.dart` files for all models. No errors.

- [ ] **Step 10: Commit**

```
git add carpool-mobile/lib/core/models/
git commit -m "feat: add core freezed DTOs (enums, user, ride, booking, hub, rating, vehicle)"
```

---

## Task 4: ApiException + DioClient

**Files:**
- Create: `carpool-mobile/lib/core/api/api_exception.dart`
- Create: `carpool-mobile/lib/core/api/dio_client.dart`

- [ ] **Step 1: Create `api_exception.dart`**

```dart
class ApiException implements Exception {
  final int? statusCode;
  final String message;
  final Map<String, dynamic>? body;

  const ApiException({this.statusCode, required this.message, this.body});

  @override
  String toString() => 'ApiException($statusCode): $message';
}
```

- [ ] **Step 2: Create `dio_client.dart`**

```dart
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../config/app_config.dart';
import '../auth/token_storage.dart';
import 'api_exception.dart';

part 'dio_client.g.dart';

@Riverpod(keepAlive: true)
Dio dioClient(DioClientRef ref) {
  final storage = ref.watch(tokenStorageProvider);
  final dio = Dio(BaseOptions(
    baseUrl: AppConfig.baseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
  ));

  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) async {
      final token = await storage.read();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      handler.next(options);
    },
    onError: (error, handler) async {
      if (error.response?.statusCode == 401) {
        final path = error.requestOptions.path;
        // Only force logout on auth/session endpoints. A 403 on an unrelated
        // endpoint (e.g. a resource the user lost access to) must NOT clear the token.
        final isAuthPath = path.contains('/auth/') || path.contains('/users/me');
        if (isAuthPath) {
          await storage.delete();
          // GoRouterNotifier fires when authNotifierProvider is next read and
          // finds no token — no explicit navigation needed here.
        }
      }
      handler.next(error);
    },
  ));

  return dio;
}

ApiException dioErrorToApiException(DioException e) {
  final status = e.response?.statusCode;
  final data = e.response?.data;
  final message = (data is Map && data['message'] != null)
      ? data['message'] as String
      : e.message ?? 'Unknown error';
  return ApiException(statusCode: status, message: message, body: data is Map ? Map<String, dynamic>.from(data) : null);
}
```

- [ ] **Step 3: Run code generation**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

Expected: generates `dio_client.g.dart`.

- [ ] **Step 4: Commit**

```
git add carpool-mobile/lib/core/api/
git commit -m "feat: add ApiException and DioClient with JWT Bearer interceptor"
```

---

## Task 5: TokenStorage

**Files:**
- Create: `carpool-mobile/lib/core/auth/token_storage.dart`

- [ ] **Step 1: Create `token_storage.dart`**

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'token_storage.g.dart';

const _kTokenKey = 'jwt_token';

@Riverpod(keepAlive: true)
TokenStorage tokenStorage(TokenStorageRef ref) => TokenStorage();

class TokenStorage {
  TokenStorage()
      : _storage = const FlutterSecureStorage(
          aOptions: AndroidOptions(encryptedSharedPreferences: true),
        );

  TokenStorage.withStorage(this._storage);

  final FlutterSecureStorage _storage;

  Future<String?> read() => _storage.read(key: _kTokenKey);
  Future<void> write(String token) => _storage.write(key: _kTokenKey, value: token);
  Future<void> delete() => _storage.delete(key: _kTokenKey);
}
```

- [ ] **Step 2: Run code generation**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

Expected: generates `token_storage.g.dart`.

- [ ] **Step 3: Commit**

```
git add carpool-mobile/lib/core/auth/token_storage.dart carpool-mobile/lib/core/auth/token_storage.g.dart
git commit -m "feat: add TokenStorage (flutter_secure_storage wrapper)"
```

---

## Task 6: AuthRepository

**Files:**
- Create: `carpool-mobile/lib/core/auth/auth_repository.dart`

- [ ] **Step 1: Create `auth_repository.dart`**

```dart
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../api/dio_client.dart';
import '../api/api_exception.dart';
import '../models/user.dart';
import 'token_storage.dart';

part 'auth_repository.g.dart';

@riverpod
AuthRepository authRepository(AuthRepositoryRef ref) {
  return AuthRepository(
    dio: ref.watch(dioClientProvider),
    storage: ref.watch(tokenStorageProvider),
  );
}

class AuthRepository {
  final Dio _dio;
  final TokenStorage _storage;

  AuthRepository({required Dio dio, required TokenStorage storage})
      : _dio = dio,
        _storage = storage;

  Future<String> loginWithTelegram(Map<String, dynamic> payload) async {
    try {
      final response = await _dio.post('/auth/telegram', data: payload);
      final token = response.data['token'] as String;
      await _storage.write(token);
      return token;
    } on DioException catch (e) {
      throw dioErrorToApiException(e);
    }
  }

  Future<AuthUser> getMe() async {
    try {
      final response = await _dio.get('/users/me');
      return AuthUser.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw dioErrorToApiException(e);
    }
  }

  Future<void> logout() async {
    await _storage.delete();
  }
}
```

- [ ] **Step 2: Run code generation**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

- [ ] **Step 3: Commit**

```
git add carpool-mobile/lib/core/auth/auth_repository.dart carpool-mobile/lib/core/auth/auth_repository.g.dart
git commit -m "feat: add AuthRepository (loginWithTelegram, getMe, logout)"
```

---

## Task 7: AuthProvider

**Files:**
- Create: `carpool-mobile/lib/core/auth/auth_provider.dart`

- [ ] **Step 1: Create `auth_provider.dart`**

```dart
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../models/user.dart';
import '../models/enums.dart';
import '../api/api_exception.dart';
import 'auth_repository.dart';
import 'token_storage.dart';

part 'auth_provider.g.dart';

@Riverpod(keepAlive: true)
class AuthNotifier extends _$AuthNotifier {
  @override
  Future<AuthUser?> build() async {
    final storage = ref.watch(tokenStorageProvider);
    final token = await storage.read();
    if (token == null) return null;

    try {
      return await ref.watch(authRepositoryProvider).getMe();
    } on ApiException catch (e) {
      if (e.statusCode == 401) {
        await storage.delete();
        return null;
      }
      rethrow;
    }
  }

  Future<void> loginWithTelegram(Map<String, dynamic> payload) async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      await ref.read(authRepositoryProvider).loginWithTelegram(payload);
      return ref.read(authRepositoryProvider).getMe();
    });
  }

  Future<void> logout() async {
    await ref.read(authRepositoryProvider).logout();
    state = const AsyncValue.data(null);
  }
}

extension AuthUserX on AuthUser {
  bool get isDriver => role == UserRole.driver || role == UserRole.both;
  bool get isPassenger => role == UserRole.passenger || role == UserRole.both;
}
```

- [ ] **Step 2: Run code generation**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

- [ ] **Step 3: Commit**

```
git add carpool-mobile/lib/core/auth/auth_provider.dart carpool-mobile/lib/core/auth/auth_provider.g.dart
git commit -m "feat: add AuthNotifier (Riverpod AsyncNotifier) with isDriver/isPassenger helpers"
```

---

## Task 8: LoginScreen

**Files:**
- Create: `carpool-mobile/lib/features/auth/login_screen.dart`

- [ ] **Step 1: Create `login_screen.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/config/app_config.dart';
import '../../core/auth/auth_provider.dart';
import '../../core/auth/token_storage.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  bool _loading = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      body: SafeArea(
        child: Stack(
          children: [
            InAppWebView(
              initialUrlRequest: URLRequest(
                url: WebUri(AppConfig.loginUrl),
              ),
              initialSettings: InAppWebViewSettings(
                useShouldOverrideUrlLoading: true,
                javaScriptEnabled: true,
              ),
              shouldOverrideUrlLoading: (controller, action) async {
                final uri = action.request.url;
                if (uri != null && uri.scheme == AppConfig.authRedirectScheme) {
                  await _handleAuthRedirect(uri);
                  return NavigationActionPolicy.CANCEL;
                }
                return NavigationActionPolicy.ALLOW;
              },
            ),
            if (_loading)
              const Center(child: CircularProgressIndicator()),
          ],
        ),
      ),
    );
  }

  Future<void> _handleAuthRedirect(WebUri uri) async {
    final token = uri.queryParameters['token'];
    if (token == null || token.isEmpty) return;

    setState(() => _loading = true);
    try {
      await ref.read(tokenStorageProvider).write(token);
      // Invalidate forces authNotifierProvider to re-run build(), which calls getMe().
      // The GoRouterNotifier listener fires, redirect re-evaluates, and the router
      // navigates to /home automatically — no explicit context.go() needed.
      ref.invalidate(authNotifierProvider);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }
}
```

> **Cross-team dependency:** The `carpool://auth?token=<jwt>` redirect requires the Next.js `/login` page to redirect to this scheme after `POST /api/v1/auth/telegram` succeeds. Until deployed, set `AppConfig.devBypassToken` (see Task 2) to bypass the WebView for smoke testing.

- [ ] **Step 2: Commit**

```
git add carpool-mobile/lib/features/auth/
git commit -m "feat: add LoginScreen (InAppWebView Telegram Login Widget + carpool:// intercept)"
```

---

## Task 9: go_router with auth redirect guard

**Files:**
- Create: `carpool-mobile/lib/core/router/router.dart`

- [ ] **Step 1: Create stub screens for tabs**

Create these four files (identical pattern — differ only in display name):

`carpool-mobile/lib/features/rides/rides_stub_screen.dart`:
```dart
import 'package:flutter/material.dart';
class RidesStubScreen extends StatelessWidget {
  const RidesStubScreen({super.key});
  @override
  Widget build(BuildContext context) =>
      const Center(child: Text('Find Ride — coming soon'));
}
```

`carpool-mobile/lib/features/bookings/bookings_stub_screen.dart`:
```dart
import 'package:flutter/material.dart';
class BookingsStubScreen extends StatelessWidget {
  const BookingsStubScreen({super.key});
  @override
  Widget build(BuildContext context) =>
      const Center(child: Text('My Bookings — coming soon'));
}
```

`carpool-mobile/lib/features/profile/profile_stub_screen.dart`:
```dart
import 'package:flutter/material.dart';
class ProfileStubScreen extends StatelessWidget {
  const ProfileStubScreen({super.key});
  @override
  Widget build(BuildContext context) =>
      const Center(child: Text('Profile — coming soon'));
}
```

`carpool-mobile/lib/features/my_rides/my_rides_stub_screen.dart`:
```dart
import 'package:flutter/material.dart';
class MyRidesStubScreen extends StatelessWidget {
  const MyRidesStubScreen({super.key});
  @override
  Widget build(BuildContext context) =>
      const Center(child: Text('My Rides — coming soon'));
}
```

- [ ] **Step 2: Create `splash_screen.dart`**

Create `carpool-mobile/lib/features/splash/splash_screen.dart`:

```dart
import 'package:flutter/material.dart';

class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(child: CircularProgressIndicator()),
    );
  }
}
```

- [ ] **Step 3: Create `router.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../auth/auth_provider.dart';
import '../models/user.dart';
import '../../features/auth/login_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/splash/splash_screen.dart';

part 'router.g.dart';

// Auth state machine — GoRouterNotifier drives redirect re-evaluation:
//
//   App cold start
//       │
//   AsyncLoading ──────────────────► /splash  (CircularProgressIndicator)
//       │                                │
//       │                                │ build() resolves: token missing → null
//       │                                │                   token present → getMe()
//       │                                ▼
//   valueOrNull == null ─────────► /login   (WebView: Telegram Login Widget)
//       │                                │
//       │                                │ carpool:// intercept
//       │                                │   → tokenStorage.write(jwt)
//       │                                │   → ref.invalidate(authNotifierProvider)
//       │                                │   → GoRouterNotifier.notifyListeners()
//       │                                │   → build() re-runs → getMe() → AuthUser
//       ▼                                ▼
//   valueOrNull != null ─────────► /home    (bottom nav tab shell)
//
// GoRouterNotifier: ChangeNotifier that listens to authNotifierProvider via
// ref.listen, calls notifyListeners() on any auth state change, and is wired
// to GoRouter.refreshListenable. This keeps the GoRouter instance stable —
// it is created once and never recreated.

class GoRouterNotifier extends ChangeNotifier {
  GoRouterNotifier(this._ref) {
    _ref.listen<AsyncValue<AuthUser?>>(authNotifierProvider, (_, __) {
      notifyListeners();
    });
  }

  final Ref _ref;
}

@Riverpod(keepAlive: true)
GoRouter router(RouterRef ref) {
  final notifier = GoRouterNotifier(ref);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: notifier,
    redirect: (context, state) {
      final authState = ref.read(authNotifierProvider);
      final isLoading = authState is AsyncLoading;
      final isLoggedIn = authState.valueOrNull != null;
      final location = state.matchedLocation;

      if (isLoading && location != '/splash') return '/splash';
      if (!isLoading && !isLoggedIn && location != '/login') return '/login';
      if (!isLoading && isLoggedIn && (location == '/login' || location == '/splash')) return '/home';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (context, state) => const SplashScreen()),
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      GoRoute(path: '/home', builder: (context, state) => const HomeScreen()),
    ],
  );
}
```

> **Why stable GoRouter?** If `routerProvider` used `ref.watch(authNotifierProvider)` and returned a new `GoRouter` instance on each auth change, the router rebuilt from scratch — wiping the navigation stack and causing a visible flash. With `refreshListenable`, the same GoRouter instance re-evaluates `redirect` without rebuilding, so the tab history is preserved.

- [ ] **Step 4: Run code generation**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

Expected: generates `router.g.dart`.

- [ ] **Step 5: Commit**

```
git add carpool-mobile/lib/core/router/ carpool-mobile/lib/features/splash/ carpool-mobile/lib/features/rides/ carpool-mobile/lib/features/bookings/ carpool-mobile/lib/features/profile/ carpool-mobile/lib/features/my_rides/
git commit -m "feat: add go_router with GoRouterNotifier, splash route, and auth redirect guard"
```

---

## Task 10: HomeScreen (tab shell)

**Files:**
- Create: `carpool-mobile/lib/features/home/home_screen.dart`

- [ ] **Step 1: Create `home_screen.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/auth/auth_provider.dart';
import '../rides/rides_stub_screen.dart';
import '../bookings/bookings_stub_screen.dart';
import '../profile/profile_stub_screen.dart';
import '../my_rides/my_rides_stub_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(authNotifierProvider).valueOrNull;
    final isDriver = user?.isDriver ?? false;

    final tabs = [
      const RidesStubScreen(),
      if (isDriver) const MyRidesStubScreen(),
      const BookingsStubScreen(),
      const ProfileStubScreen(),
    ];

    final navItems = [
      const BottomNavigationBarItem(icon: Icon(Icons.search), label: 'Find Ride'),
      if (isDriver)
        const BottomNavigationBarItem(icon: Icon(Icons.directions_car), label: 'My Rides'),
      const BottomNavigationBarItem(icon: Icon(Icons.book_online), label: 'Bookings'),
      const BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Profile'),
    ];

    final safeIndex = _currentIndex.clamp(0, tabs.length - 1);

    return Scaffold(
      body: tabs[safeIndex],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: safeIndex,
        onTap: (i) => setState(() => _currentIndex = i),
        type: BottomNavigationBarType.fixed,
        items: navItems,
      ),
    );
  }
}
```

- [ ] **Step 2: Commit**

```
git add carpool-mobile/lib/features/home/
git commit -m "feat: add HomeScreen tab shell (3 tabs Passenger, 4 tabs Driver/Both)"
```

---

## Task 11: Wire app.dart + main.dart

**Files:**
- Modify: `carpool-mobile/lib/main.dart`
- Create: `carpool-mobile/lib/app.dart`

- [ ] **Step 1: Create `app.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/router/router.dart';

class CarpoolApp extends ConsumerWidget {
  const CarpoolApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    return MaterialApp.router(
      title: 'Car-E-Pool',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2563EB)),
        useMaterial3: true,
      ),
      routerConfig: router,
    );
  }
}
```

- [ ] **Step 2: Update `main.dart`**

Replace the generated `main.dart` content with:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: CarpoolApp()));
}
```

- [ ] **Step 3: Verify the app compiles**

```bash
cd carpool-mobile && flutter build apk --debug
```

Expected: `BUILD SUCCESSFUL` — produces `build/app/outputs/flutter-apk/app-debug.apk`. No compile errors.

- [ ] **Step 4: Smoke test on emulator or device**

```bash
cd carpool-mobile && flutter run
```

Expected: App launches, shows the WebView login screen (since no JWT is stored yet). Tapping "Log in with Telegram" renders the widget.

- [ ] **Step 5: Commit**

```
git add carpool-mobile/lib/main.dart carpool-mobile/lib/app.dart
git commit -m "feat: wire CarpoolApp (MaterialApp.router + ProviderScope) — Flutter Foundation complete"
```

---

---

## Task 12: Foundation tests

**Files:**
- Modify: `carpool-mobile/pubspec.yaml` (mockito + http_mock_adapter already added in Task 1)
- Create: `carpool-mobile/test/core/auth/token_storage_test.dart`
- Create: `carpool-mobile/test/core/auth/auth_notifier_test.dart`
- Create: `carpool-mobile/test/core/router/router_redirect_test.dart`
- Create: `carpool-mobile/test/features/home/home_screen_test.dart`

- [ ] **Step 1: Create `token_storage_test.dart`**

```dart
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:carpool_mobile/core/auth/token_storage.dart';

@GenerateMocks([FlutterSecureStorage])
import 'token_storage_test.mocks.dart';

void main() {
  late MockFlutterSecureStorage mockStorage;
  late TokenStorage tokenStorage;

  setUp(() {
    mockStorage = MockFlutterSecureStorage();
    tokenStorage = TokenStorage.withStorage(mockStorage);
  });

  test('read returns null when no token stored', () async {
    when(mockStorage.read(key: 'jwt_token')).thenAnswer((_) async => null);
    expect(await tokenStorage.read(), isNull);
  });

  test('read returns token when stored', () async {
    when(mockStorage.read(key: 'jwt_token'))
        .thenAnswer((_) async => 'test.jwt.token');
    expect(await tokenStorage.read(), equals('test.jwt.token'));
  });

  test('write stores token via underlying storage', () async {
    when(mockStorage.write(key: 'jwt_token', value: 'test.jwt.token'))
        .thenAnswer((_) async {});
    await tokenStorage.write('test.jwt.token');
    verify(mockStorage.write(key: 'jwt_token', value: 'test.jwt.token'))
        .called(1);
  });

  test('delete removes token', () async {
    when(mockStorage.delete(key: 'jwt_token')).thenAnswer((_) async {});
    await tokenStorage.delete();
    verify(mockStorage.delete(key: 'jwt_token')).called(1);
  });
}
```

- [ ] **Step 2: Run code generation for mocks**

```bash
cd carpool-mobile && flutter pub run build_runner build --delete-conflicting-outputs
```

Expected: generates `token_storage_test.mocks.dart`.

- [ ] **Step 3: Create `auth_notifier_test.dart`**

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:carpool_mobile/core/auth/auth_provider.dart';
import 'package:carpool_mobile/core/auth/auth_repository.dart';
import 'package:carpool_mobile/core/auth/token_storage.dart';
import 'package:carpool_mobile/core/models/user.dart';
import 'package:carpool_mobile/core/models/enums.dart';
import 'package:carpool_mobile/core/api/api_exception.dart';

@GenerateMocks([AuthRepository, TokenStorage])
import 'auth_notifier_test.mocks.dart';

final _testUser = AuthUser(
  id: 1,
  firstName: 'Test',
  role: UserRole.passenger,
  termsAccepted: true,
);

void main() {
  late MockAuthRepository mockRepo;
  late MockTokenStorage mockStorage;

  setUp(() {
    mockRepo = MockAuthRepository();
    mockStorage = MockTokenStorage();
  });

  ProviderContainer makeContainer() => ProviderContainer(overrides: [
        authRepositoryProvider.overrideWithValue(mockRepo),
        tokenStorageProvider.overrideWithValue(mockStorage),
      ]);

  test('build: no token → returns null', () async {
    when(mockStorage.read()).thenAnswer((_) async => null);
    final container = makeContainer();
    addTearDown(container.dispose);
    final state = await container.read(authNotifierProvider.future);
    expect(state, isNull);
  });

  test('build: valid token → calls getMe → returns AuthUser', () async {
    when(mockStorage.read()).thenAnswer((_) async => 'valid.jwt');
    when(mockRepo.getMe()).thenAnswer((_) async => _testUser);
    final container = makeContainer();
    addTearDown(container.dispose);
    final state = await container.read(authNotifierProvider.future);
    expect(state, equals(_testUser));
  });

  test('build: token + 401 from getMe → clears token → returns null', () async {
    when(mockStorage.read()).thenAnswer((_) async => 'expired.jwt');
    when(mockRepo.getMe()).thenThrow(
        const ApiException(statusCode: 401, message: 'Unauthorized'));
    when(mockStorage.delete()).thenAnswer((_) async {});
    final container = makeContainer();
    addTearDown(container.dispose);
    final state = await container.read(authNotifierProvider.future);
    expect(state, isNull);
    verify(mockStorage.delete()).called(1);
  });

  test('logout: sets auth state to null', () async {
    when(mockStorage.read()).thenAnswer((_) async => 'valid.jwt');
    when(mockRepo.getMe()).thenAnswer((_) async => _testUser);
    when(mockRepo.logout()).thenAnswer((_) async {});
    final container = makeContainer();
    addTearDown(container.dispose);
    await container.read(authNotifierProvider.future);
    await container.read(authNotifierProvider.notifier).logout();
    final state = await container.read(authNotifierProvider.future);
    expect(state, isNull);
  });

  test('new user: rating fields default to 0.0, not throw', () async {
    final newUser = AuthUser(
      id: 99,
      firstName: 'New',
      role: UserRole.passenger,
      termsAccepted: false,
      // averageRatingAsDriver, averageRatingAsPassenger use @Default(0.0) —
      // they must NOT be required params, and the object must construct cleanly.
    );
    expect(newUser.averageRatingAsDriver, 0.0);
    expect(newUser.averageRatingAsPassenger, 0.0);
  });
}
```

- [ ] **Step 4: Create `router_redirect_test.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:carpool_mobile/core/auth/auth_provider.dart';
import 'package:carpool_mobile/core/models/user.dart';
import 'package:carpool_mobile/core/models/enums.dart';
import 'package:carpool_mobile/core/router/router.dart';

final _testUser = AuthUser(
  id: 1, firstName: 'Test', role: UserRole.passenger, termsAccepted: true,
);

class _FakeAuthNotifier extends AuthNotifier {
  _FakeAuthNotifier(this._initialState);
  final AsyncValue<AuthUser?> _initialState;
  @override
  Future<AuthUser?> build() async => _initialState.valueOrNull;
}

ProviderContainer makeContainer(AsyncValue<AuthUser?> auth) =>
    ProviderContainer(overrides: [
      authNotifierProvider.overrideWith(() => _FakeAuthNotifier(auth)),
    ]);

void main() {
  testWidgets('unauthenticated: redirect from /home → /login', (tester) async {
    final container = makeContainer(const AsyncData(null));
    addTearDown(container.dispose);
    final router = container.read(routerProvider);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    expect(router.routeInformationProvider.value.uri.path, equals('/login'));
  });

  testWidgets('authenticated: redirect from /login → /home', (tester) async {
    final container = makeContainer(AsyncData(_testUser));
    addTearDown(container.dispose);
    final router = container.read(routerProvider);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    expect(router.routeInformationProvider.value.uri.path, equals('/home'));
  });

  testWidgets('loading state: stays on /splash', (tester) async {
    final container = makeContainer(const AsyncLoading());
    addTearDown(container.dispose);
    final router = container.read(routerProvider);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pump();

    expect(router.routeInformationProvider.value.uri.path, equals('/splash'));
  });
}
```

- [ ] **Step 5: Create `home_screen_test.dart`**

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:carpool_mobile/core/auth/auth_provider.dart';
import 'package:carpool_mobile/core/models/user.dart';
import 'package:carpool_mobile/core/models/enums.dart';
import 'package:carpool_mobile/features/home/home_screen.dart';

class _FakeAuthNotifier extends AuthNotifier {
  _FakeAuthNotifier(this._user);
  final AuthUser _user;
  @override
  Future<AuthUser?> build() async => _user;
}

Widget makeApp(AuthUser user) => ProviderScope(
      overrides: [
        authNotifierProvider.overrideWith(() => _FakeAuthNotifier(user)),
      ],
      child: const MaterialApp(home: HomeScreen()),
    );

void main() {
  testWidgets('Passenger role shows 3 nav items, no My Rides', (tester) async {
    final user = AuthUser(
      id: 1, firstName: 'Test', role: UserRole.passenger, termsAccepted: true,
    );
    await tester.pumpWidget(makeApp(user));
    await tester.pumpAndSettle();

    final navBar = tester.widget<BottomNavigationBar>(
        find.byType(BottomNavigationBar));
    expect(navBar.items.length, 3);
    expect(find.text('Find Ride'), findsOneWidget);
    expect(find.text('Bookings'), findsOneWidget);
    expect(find.text('Profile'), findsOneWidget);
    expect(find.text('My Rides'), findsNothing);
  });

  testWidgets('Driver role shows 4 nav items including My Rides', (tester) async {
    final user = AuthUser(
      id: 2, firstName: 'Driver', role: UserRole.driver, termsAccepted: true,
    );
    await tester.pumpWidget(makeApp(user));
    await tester.pumpAndSettle();

    final navBar = tester.widget<BottomNavigationBar>(
        find.byType(BottomNavigationBar));
    expect(navBar.items.length, 4);
    expect(find.text('My Rides'), findsOneWidget);
  });

  testWidgets('Both role shows 4 nav items', (tester) async {
    final user = AuthUser(
      id: 3, firstName: 'Both', role: UserRole.both, termsAccepted: true,
    );
    await tester.pumpWidget(makeApp(user));
    await tester.pumpAndSettle();

    final navBar = tester.widget<BottomNavigationBar>(
        find.byType(BottomNavigationBar));
    expect(navBar.items.length, 4);
  });
}
```

- [ ] **Step 6: Run all tests**

```bash
cd carpool-mobile && flutter test
```

Expected output:
```
00:05 +12: All tests passed!
```

If `token_storage_test` fails with "MissingStubError" — verify `@GenerateMocks` targets `FlutterSecureStorage` and build_runner ran successfully.
If `router_redirect_test` fails with "No MaterialApp ancestor" — verify `MaterialApp.router` wraps `UncontrolledProviderScope`.

- [ ] **Step 7: Commit**

```
git add carpool-mobile/test/
git commit -m "test: add foundation tests — TokenStorage, AuthNotifier, router redirect, HomeScreen tabs"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by task |
|-----------------|----------------|
| Flutter scaffold + pubspec | Task 1 |
| App config (baseUrl, loginUrl, devBypassToken) | Task 2 |
| All freezed DTO models | Task 3 |
| DioClient + JWT interceptor + scoped 401 handler | Task 4 |
| TokenStorage (flutter_secure_storage + withStorage) | Task 5 |
| AuthRepository (loginWithTelegram, getMe) | Task 6 |
| AuthProvider (AsyncNotifier, keepAlive, isDriver) | Task 7 |
| LoginScreen (WebView + carpool:// intercept, ref.invalidate) | Task 8 |
| GoRouterNotifier + stable GoRouter + /splash + redirect guard | Task 9 |
| Home shell (3/4 tab bottom nav by role) | Task 10 |
| ProviderScope in app.dart + main.dart | Task 11 |
| TokenStorage, AuthNotifier, router redirect, HomeScreen tests | Task 12 |

**Placeholder scan:** No TBDs. All code blocks are complete.

**Type consistency:** `AuthUser` defined in Task 3 with `@Default(0.0)` rating fields, consumed in Tasks 7, 8, 10, 12. `TokenStorage.withStorage` added in Task 5, used in Task 12 tests. `GoRouterNotifier` defined in Task 9 and referenced by `GoRouter.refreshListenable`. All consistent.

**Cross-team dependency:** `carpool://auth?token=<jwt>` redirect requires Next.js update. Dev bypass via `AppConfig.devBypassToken` documented in Task 2.

---

## GSTACK REVIEW REPORT

| Review | Runs | Outcome |
|--------|------|---------|
| CEO Review | 0 | — |
| Codex Review (outside voice) | 1 | 10 findings — 4 dismissed/TODOs, 6 fixed in plan |
| Eng Review | 1 | 14 decisions (D1–D14) — 6 P1 bugs fixed, 2 deferred to TODOS |
| Design Review | 0 | — |
| DX Review | 0 | — |

**VERDICT: ENG CLEARED**

### Issues found and resolved

| ID | Source | Severity | Fix |
|----|--------|----------|-----|
| GoRouter recreation on auth change | Eng Review D2 + Outside Voice D12 | P1 | Replaced with GoRouterNotifier + refreshListenable + stable GoRouter instance |
| Provider auto-dispose on navigation | Eng Review D3 | P1 | `@Riverpod(keepAlive: true)` on authNotifier, tokenStorage, dioClient, router |
| `notifier.build()` called directly | Eng Review D4 | P1 | Replaced with `ref.invalidate(authNotifierProvider)` |
| Cold-start login flash (AsyncLoading) | Eng Review D7 | P1 | Added /splash route; redirect: AsyncLoading → /splash |
| New user rating nullability crash | Outside Voice D10 | P1 | `@Default(0.0)` on averageRatingAsDriver/averageRatingAsPassenger |
| Broad 401 logout (all endpoints) | Outside Voice D11 | P1 | Scoped 401 handler to `/auth/` and `/users/me` paths only |
| No test coverage | Eng Review D6 | P1 | Task 12 added — 15 tests across 4 files |
| Next.js Day 1 dependency | Outside Voice D9 | P1 | `AppConfig.devBypassToken` debug bypass documented in Task 2 |
| Flutter CI/CD absent | Eng Review D5 / D13 | P2 | Deferred → TODOS.md (P2) |
| WebView error handling absent | Outside Voice / D14 | P2 | Deferred → TODOS.md (P2) |

### Decisions log

| D# | Decision | Chosen |
|----|----------|--------|
| D1 | Scope — full feature-first scaffold | Proceed as designed |
| D2 | Router stability | Stable GoRouter + GoRouterRefreshStream |
| D3 | Provider keepAlive | keepAlive on auth + token + dio |
| D4 | Login auth refresh call | ref.invalidate + remove context.go |
| D5 | CI/CD timing | Defer to follow-up |
| D6 | Test coverage | Add Task 12 now |
| D7 | Cold-start flash | Add /splash route |
| D8 | Outside voice | Run it (Claude subagent) |
| D9 | Next.js dependency | Dev bypass + document dependency |
| D10 | Rating nullability | @Default(0.0) |
| D11 | 401 logout scope | Scope to /auth/ and /users/me |
| D12 | GoRouterNotifier wiring | Add to Task 9 |
| D13 | Flutter CI TODO | Add to TODOS.md P2 |
| D14 | WebView error handling TODO | Add to TODOS.md P2 |

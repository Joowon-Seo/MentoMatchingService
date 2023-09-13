package com.github.supercodingfinalprojectbackend.service;

import com.github.supercodingfinalprojectbackend.dto.*;
import com.github.supercodingfinalprojectbackend.entity.*;
import com.github.supercodingfinalprojectbackend.entity.type.SkillStackType;
import com.github.supercodingfinalprojectbackend.entity.type.SocialPlatformType;
import com.github.supercodingfinalprojectbackend.entity.type.UserRole;
import com.github.supercodingfinalprojectbackend.exception.errorcode.ApiErrorCode;
import com.github.supercodingfinalprojectbackend.repository.*;
import com.github.supercodingfinalprojectbackend.util.ValidateUtils;
import com.github.supercodingfinalprojectbackend.util.auth.AuthHolder;
import com.github.supercodingfinalprojectbackend.util.jwt.JwtUtils;
import com.github.supercodingfinalprojectbackend.util.jwt.TokenHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class Oauth2Service {

    @Value("${spring.security.oauth2.client.provider.kakao.token-uri}")
    private String kakaoTokenUri;
    private final String kakaoGrantType = "authorization_code";
    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;
    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String kakaoRedirectUri;
    @Value("${spring.security.oauth2.client.provider.kakao.user-info-uri}")
    private String kakaoUserInfoUri;
    @Value("${spring.security.oauth2.client.provider.kakao.logout-uri}")
    private String kakaoLogoutUri;

    private final MenteeRepository menteeRepository;
    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final UserSocialInfoRepository userSocialInfoRepository;
    private final UserAbstractAccountRepository userAbstractAccountRepository;
    private final MentorRepository mentorRepository;
    private final SkillStackRepository skillStackRepository;
    private final MentorSkillStackRepository mentorSkillStackRepository;
    private final MentorCareerRepository mentorCareerRepository;
    private final SecretKey secretKey;
    @Qualifier("AuthHolder")
    private final AuthHolder authHolder;

    public Login.Response kakaoLogin(String code) {
        ValidateUtils.requireNotNull(code, 401, "카카오 로그인에 실패했습니다.");

        Kakao.OauthToken kakaoOauthToken = getKakaoToken(code);
        Kakao.UserInfo kakaoUserInfo = getKakaoUserInfo(kakaoOauthToken);

        // 회원이 존재하지 않으면 회원 가입
        Long kakaoId = kakaoUserInfo.getId();
        UserSocialInfo userSocialInfo = userSocialInfoRepository.findBySocialIdAndSocialPlatformNameAndIsDeletedIsFalse(kakaoId, SocialPlatformType.KAKAO.resolve().name())
                .orElseGet(()->{
                    Kakao.Account kakaoAccount = kakaoUserInfo.getKakaoAccount();
                    Kakao.Profile kakaoProfile = kakaoAccount.getProfile();

                    UserAbstractAccount userAbstractAccount = userAbstractAccountRepository.save(UserAbstractAccount.of());
                    User user = userRepository.save(User.of(userAbstractAccount, kakaoAccount.getEmail(), kakaoProfile.getNickname(), kakaoProfile.getThumbnailImageUrl()));
                    menteeRepository.save(Mentee.from(user));

                    return userSocialInfoRepository.save(UserSocialInfo.of(user, kakaoUserInfo.getId(), SocialPlatformType.KAKAO.resolve()));
                });

        Login login = serviceLogin(userSocialInfo.getUser());
        kakaoLogout(kakaoOauthToken.getAccessToken());

        return Login.Response.from(login);
    }

    public Login serviceLogin(User user) {
        // 이전 로그인 기록을 뒤져서 어떤 역할로 로그인할 것인지 선택
        LoginRecord loginRecord = loginRecordRepository.findFirstByUserAndIsDeletedIsFalseOrderByCreatedAtDesc(user).orElse(null);
        UserRole userRole = loginRecord == null ? UserRole.MENTEE.resolve() : UserRole.valueOf(loginRecord.getRoleName()).resolve();

        // 토큰 생성
        Long userId = user.getUserId();
        String userIdString = userId.toString();
        Set<String> authorities = Set.of(userRole.name());
        TokenHolder tokenHolder = JwtUtils.createTokens(userIdString, authorities, secretKey);

        // 메모리에 로그인 정보 저장
        Login login = Login.of(userRole, tokenHolder);
        authHolder.put(userId, login);

        // DB에 로그인 기록 저장
        loginRecordRepository.save(LoginRecord.of(user, userRole));

        return login;
    }

    private Kakao.OauthToken getKakaoToken(String code) {
        ValidateUtils.requireNotNull(code, 500, "code는 null일 수 없습니다.");

        RestTemplate restTemplate = new RestTemplate();
        RequestEntity<MultiValueMap<String, String>> request = createKakaoTokenRequest(code);
        ResponseEntity<Kakao.OauthToken> response = ValidateUtils.requireApply(request, o->restTemplate.exchange(o, Kakao.OauthToken.class), 500, "카카오 토큰 요청에 실패했습니다.");
        Kakao.OauthToken kakaoOauthToken = response.getBody();
        return ValidateUtils.requireNotNull(kakaoOauthToken, 500, "카카오 토큰 요청에 실패했습니다.");
    }

    private RequestEntity<MultiValueMap<String, String>> createKakaoTokenRequest(String code) {
        ValidateUtils.requireNotNull(code, 500, "code는 null일 수 없습니다.");

        URI uri = ValidateUtils.requireApply(kakaoTokenUri, URI::create, 500,"카카오 토큰 요청에 실패했습니다.");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", kakaoGrantType);
        body.add("client_id", kakaoClientId);
        body.add("redirect_uri", kakaoRedirectUri);
        body.add("code", code);

        return RequestEntity.post(uri).headers(headers).body(body);
    }

    private Kakao.UserInfo getKakaoUserInfo(Kakao.OauthToken kakaoOauthToken) {
        RequestEntity<?> request = createKakaoUserInfoRequest(kakaoOauthToken);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Kakao.UserInfo> response = ValidateUtils.requireApply(request, r->restTemplate.exchange(r, Kakao.UserInfo.class), 500, "카카오 유저 정보 요청에 실패했습니다.");

        Kakao.UserInfo kakaoUserInfo = response.getBody();
        if (kakaoUserInfo == null) throw ApiErrorCode.NOT_FOUND_USER_INFO.exception();
        return ValidateUtils.requireNotNull(kakaoUserInfo, 500, "카카오 유저 정보를 불러오는 데 실패했습니다.");
    }

    private RequestEntity<Void> createKakaoUserInfoRequest(Kakao.OauthToken kakaoOauthToken) {

        URI uri = ValidateUtils.requireApply(kakaoUserInfoUri, URI::create, 500, "카카오 유저 정보 요청 uri를 생성하지 못했습니다.");
        ValidateUtils.requireNotNull(kakaoOauthToken, 500, "kakaoOauthToken은 null일 수 없습니다.");
        String accessToken = kakaoOauthToken.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        return RequestEntity.get(uri).headers(headers).build();
    }

    public void kakaoLogout(String kakaoAccessToken) {
        URI uri = ValidateUtils.requireApply(kakaoLogoutUri, URI::create, 500, "카카오 로그아웃 요청 uri를 생성하지 못했습니다.");

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        headers.add("Authorization", "Bearer " + kakaoAccessToken);
        RequestEntity<?> request = RequestEntity.get(uri).headers(headers).build();

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.exchange(request, Object.class);
    }
    public void googleLogout() {
        // TODO: 구글 로그아웃 구현
    }

    public void logout(Long userId) {
        authHolder.remove(userId);
    }

    public Login.Response switchRole(Long userId, UserRole userRole) {
        User user = userRepository.findByUserIdAndIsDeletedIsFalse(userId).orElseThrow(ApiErrorCode.NOT_FOUND_USER::exception);

        Login login = authHolder.get(userId);
        if (!login.getUserRole().equals(userRole)) {
            login = switchLogin(user, userRole);
        }

        return Login.Response.from(login);
    }

    private Login switchLogin(User user, UserRole userRole) {
        loginRecordRepository.save(LoginRecord.of(user, userRole));

        Set<String> authorities = Set.of(userRole.name());
        TokenHolder tokenHolder = JwtUtils.createTokens(user.getUserId().toString(), authorities, secretKey);
        Login newLogin = Login.of(userRole, tokenHolder);
        authHolder.put(user.getUserId(), newLogin);

        return newLogin;
    }

    public MentorDto.JoinResponse joinMentor(Long userId, MentorDto.JoinRequest request) {
        User user = userRepository.findByUserIdAndIsDeletedIsFalse(userId).orElseThrow(ApiErrorCode.NOT_FOUND_USER::exception);
        Mentor mentor = mentorRepository.save(Mentor.of(user, request.getCompany(), request.getIntroduction()));

        List<MentorCareerDto.Request> careers = request.getCareers();
        if (careers != null) {
            ValidateUtils.requireTrue(careers.stream().allMatch(MentorCareerDto.Request::validate), ApiErrorCode.INVALID_DUTY);
            List<MentorCareer> mentorCareers = careers.stream()
                    .map(c->MentorCareer.of(mentor, c))
                    .map(mentorCareerRepository::save)
                    .collect(Collectors.toList());
            mentor.setMentorCareers(mentorCareers);
        }

        List<String> skillStackNames = request.getSkillStackNames();
        if (skillStackNames != null) {
            List<SkillStack> skillStacks = skillStackNames.stream()
                    .map(skillStackName->ValidateUtils.requireApply(skillStackName, SkillStackType::valueOf, ApiErrorCode.INVALID_SKILL_STACK))
                    .map(SkillStackType::getSkillStackCode)
                    .map(c->skillStackRepository.findBySkillStackId(c).orElseThrow(ApiErrorCode.INTERNAL_SERVER_ERROR::exception))
                    .collect(Collectors.toList());

            List<MentorSkillStack> mentorSkillStacks = skillStacks.stream()
                    .map(s->MentorSkillStack.of(mentor, s))
                    .map(mentorSkillStackRepository::save)
                    .collect(Collectors.toList());
            mentor.setMentorSkillStacks(mentorSkillStacks);
        }

        return MentorDto.JoinResponse.from(mentor);
    }

    public TokenDto.Response renewTokens(String refreshToken) {
        String userIdStr = ValidateUtils.requireApply(refreshToken, t->JwtUtils.getSubject(t, secretKey), ApiErrorCode.UNRELIABLE_JWT);
        Long userId = ValidateUtils.requireApply(userIdStr, Long::parseLong, ApiErrorCode.UNRELIABLE_JWT);
        Login login = ValidateUtils.requireNotNull(authHolder.get(userId), 404, "로그인 기록이 없습니다.");
        TokenHolder tokenHolder = JwtUtils.createTokens(userIdStr, Set.of(login.getUserRole().resolve().name()), secretKey);
        Login newLogin = Login.of(login.getUserRole(), tokenHolder);
        authHolder.put(userId, newLogin);

        return TokenDto.Response.from(tokenHolder);
    }
}

package com.github.supercodingfinalprojectbackend.dto;

import com.github.supercodingfinalprojectbackend.entity.Mentor;
import com.querydsl.core.annotations.QueryProjection;
import lombok.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorDto {

	private Long mentorId;
	private Long mentorAbstractAccountId;
	private String name;
	private String nickname;
	private String email;
	private String thumbnailImageUrl;
	private String introduction;
	private String company;
	// private DutyType currentDuty;
	// private String currentPeriod;
	private Boolean searchable;

	public static MentorDto fromEntity(Mentor mentor){
		return MentorDto.builder()
				.mentorId(mentor.getMentorId())
//				Test 때 mentor 계좌가 없이 테스트 하느라 임시 주석
//				.mentorAbstractAccountId(
//						mentor.getMentorAbstractAccount().getMentorAbstractAccountId())
				.name(mentor.getUser().getName())
				.nickname(mentor.getUser().getNickname())
				.introduction(mentor.getIntroduction())
				.email(mentor.getUser().getEmail())
				.company(mentor.getCompany())
				.thumbnailImageUrl(mentor.getUser().getThumbnailImageUrl())
				.build();
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class MentorInfoResponse {

		private Long mentorId;
		private String name;
		private String nickname;
		private String email;
		private String thumbnailImageUrl;
		private String introduction;
		private String company;

		@QueryProjection
		public MentorInfoResponse(Long mentorId, String name, String introduction, String company){
			this.mentorId = mentorId;
			this.name = name;
			this.introduction = introduction;
			this.company = company;
		}

		public static MentorInfoResponse from(MentorDto mentorDto){
			return MentorInfoResponse.builder()
					.mentorId(mentorDto.getMentorId())
					.name(mentorDto.getName())
					.introduction(mentorDto.getIntroduction())
					.company(mentorDto.getCompany())
					.thumbnailImageUrl(mentorDto.getThumbnailImageUrl())
					.build();
		}
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@ToString
	public static class JoinRequest {
		private String company;
		private String introduction;
		private String currentDuty;
		private String currentPeriod;
		private Set<MentorCareerDto.Request> careers;
		private Set<String> skillStackNames;
	}
}

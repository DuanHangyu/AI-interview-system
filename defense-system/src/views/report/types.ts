export interface ReportDimension {
  name: string;
  aiScore: number;
  score: number;
  maxScore?: number | null;
  description?: string;
  /** 所在分档，如“4-5分”（按题目评分标准评分时） */
  level?: string | null;
  hitPoints?: string[] | null;
  missedPoints?: string[] | null;
}

export interface ReportQuestionDimension {
  name: string;
  level?: string | null;
  score: number;
  maxScore?: number | null;
  hitPoints?: string[] | null;
  missedPoints?: string[] | null;
  comment?: string | null;
}

export interface ReportFollowUp {
  question?: string | null;
  answer?: string | null;
  answerFile?: string | null;
}

export interface ReportQa {
  id: number;
  question?: string;
  answer?: string;
  answerFile?: string | null;
  followUps?: ReportFollowUp[] | null;
  dimensions?: ReportQuestionDimension[] | null;
  strength?: string | null;
  weakness?: string | null;
  suggestion?: string | null;
}

export interface ReportDetail {
  id: number;
  studentName?: string;
  studentAccount?: string;
  schoolClass?: string;
  theme?: string;
  interviewTime?: string;
  defenseEnabled?: boolean;
  questionEnabled?: boolean;
  score?: number | null;
  aiScore?: number | null;
  totalScore?: number;
  passScore?: number;
  summary?: string | null;
  dimensions?: ReportDimension[] | null;
  analysis?: { suggestions?: string[]; strengths?: string[]; weaknesses?: string[] } | null;
  defense?: {
    defenseAnswer?: string;
    defenseAnswerFile?: string | null;
    strength?: string | null;
    weakness?: string | null;
    suggestion?: string | null;
  } | null;
  result?: ReportQa[] | null;
  review?: {
    comment?: string | null;
    reviewerName?: string | null;
    reviewTime?: string | null;
    dimensions?: { name: string; score: number }[];
  } | null;
  showResult?: boolean;
  state?: number;
  reAnalysis?: boolean;
}

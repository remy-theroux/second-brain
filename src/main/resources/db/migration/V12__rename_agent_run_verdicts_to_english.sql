-- AnswerVerdict's constants moved to English with the project's language rule, and the
-- enum is persisted as EnumType.STRING: rows already written still carry the old names
-- and no longer read back without this update.
UPDATE knowledge_agent_runs SET verdict = 'GROUNDED' WHERE verdict = 'SOURCEE';
UPDATE knowledge_agent_runs SET verdict = 'CONVERSATIONAL' WHERE verdict = 'CONVERSATIONNELLE';
UPDATE knowledge_agent_runs SET verdict = 'UNGROUNDED' WHERE verdict = 'SANS_SOURCE';
UPDATE knowledge_agent_runs SET verdict = 'BUDGET_EXCEEDED' WHERE verdict = 'BUDGET_DEPASSE';

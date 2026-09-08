package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

/** The single field of {@code about.get?fields=user} this project reads. */
record GoogleAboutResponse(User user) {

    record User(String emailAddress) {}
}

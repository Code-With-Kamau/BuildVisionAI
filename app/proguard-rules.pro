# Gson reads these classes by reflection (proposal JSON stored in suggestions, and the cloud assistant's replies).
-keep class com.buildvisionai.collaboration.ChangeProposal { *; }
-keep class com.buildvisionai.collaboration.ProposalOp { *; }
-keep class com.buildvisionai.collaboration.ProposalOp$* { *; }
-keep class com.buildvisionai.data.remote.dto.** { *; }
-keepclassmembers enum com.buildvisionai.** { *; }

# Retrofit keeps its own rules through the library; the service interface needs its annotations kept.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep interface com.buildvisionai.data.remote.AssistantApi { *; }

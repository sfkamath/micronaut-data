standard JPA composite @JoinColumn not mapping in Micronaut Data
- jakarta @JoinColumn is @Repeatable. Two of them get compiler-wrapped in the jakarta.persistence.JoinColumns container, and there's a mapper for JoinColumn but none for the JoinColumns container — so the
  repeated form doesn't surface. Single jakarta @JoinColumn would have mapped fine; only the composite (container) form fails.
- only jakarta repeatable annotations whose container lacks a mapper need the Micronaut-native form in fixtures. That's JoinColumns here. Not a blanket rule.

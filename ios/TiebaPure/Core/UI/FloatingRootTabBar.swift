import SwiftUI

private struct FloatingRootTabSelectionKey: EnvironmentKey {
    static let defaultValue: Binding<RootTab>? = nil
}

extension EnvironmentValues {
    var floatingRootTabSelection: Binding<RootTab>? {
        get { self[FloatingRootTabSelectionKey.self] }
        set { self[FloatingRootTabSelectionKey.self] = newValue }
    }
}

extension View {
    func floatingRootTabBar(isVisible: Bool = true) -> some View {
        modifier(FloatingRootTabBarModifier(isVisible: isVisible))
    }

    func floatingGlass<S: InsettableShape>(in shape: S) -> some View {
        modifier(FloatingGlassModifier(shape: shape))
    }
}

private struct FloatingGlassModifier<S: InsettableShape>: ViewModifier {
    let shape: S
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @ViewBuilder func body(content: Content) -> some View {
        if reduceTransparency {
            content.background(Color(uiColor: .secondarySystemBackground), in: shape)
        } else if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: shape)
        } else {
            content
                .background(.ultraThinMaterial, in: shape)
                .overlay(shape.strokeBorder(.white.opacity(0.5), lineWidth: 0.5))
                .shadow(color: .black.opacity(0.12), radius: 14, y: 5)
        }
    }
}

private struct FloatingRootTabBarModifier: ViewModifier {
    let isVisible: Bool
    @Environment(\.floatingRootTabSelection) private var selection

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .ignoresSafeArea(.container, edges: .bottom)
                .toolbar(isVisible ? .visible : .hidden, for: .tabBar)
                .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
        } else {
        GeometryReader { geometry in
            // Extend the scroll viewport below the safe area; only the capsule overlays it.
            content
                .frame(width: geometry.size.width, height: geometry.size.height + geometry.safeAreaInsets.bottom)
                .overlay(alignment: .bottom) {
                    if isVisible, let selection {
                        FloatingRootTabBar(selection: selection)
                            .padding(.horizontal, 20)
                            .padding(.bottom, geometry.safeAreaInsets.bottom + 8)
                    }
                }
        }
        .toolbar(.hidden, for: .tabBar)
        .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        }
    }
}

private struct FloatingRootTabBar: View {
    @Binding var selection: RootTab

    var body: some View {
        HStack(spacing: 0) {
            tab(.home, title: "首页", symbol: "house.fill", identifier: "root-tab-home")
            tab(.forums, title: "进吧", symbol: "square.grid.2x2.fill", identifier: "root-tab-forums")
            tab(.me, title: "我的", symbol: "person.crop.circle.fill", identifier: "root-tab-me")
        }
        .padding(4)
        .frame(maxWidth: 280)
        .floatingGlass(in: Capsule())
        .accessibilityElement(children: .contain)
        .accessibilityLabel("主导航")
        .accessibilityIdentifier("floating-root-tab-bar")
    }

    private func tab(_ value: RootTab, title: String, symbol: String, identifier: String) -> some View {
        Button {
            selection = value
        } label: {
            VStack(spacing: 3) {
                Image(systemName: symbol).font(.system(size: 23, weight: .semibold))
                Text(title).font(.caption2.weight(.semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            .foregroundStyle(selection == value ? Color.blue : Color.primary)
            .background(selection == value ? Color.primary.opacity(0.08) : .clear, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(selection == value ? .isSelected : [])
        .accessibilityIdentifier(identifier)
    }
}

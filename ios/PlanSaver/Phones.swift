import Foundation

/// One model line (e.g. "iPhone 16") and the variants sold under it. "" = the base model.
struct PhoneFamily {
    let label: String
    let variants: [String]
    func name(_ variant: String) -> String { label + variant }
}

/// Phones offered in the picker, by brand. Same lists as Android's Phones.kt.
enum Phones {
    private static let iphoneVariants = ["", " Plus", " Pro", " Pro Max"]

    static let brandOrder = ["iPhone", "Samsung", "Google Pixel"]

    static let brands: [String: [PhoneFamily]] = [
        "iPhone": [
            PhoneFamily(label: "iPhone 13", variants: ["", " mini", " Pro", " Pro Max"]),
            PhoneFamily(label: "iPhone 14", variants: iphoneVariants),
            PhoneFamily(label: "iPhone 15", variants: iphoneVariants),
            PhoneFamily(label: "iPhone 16", variants: iphoneVariants),
            PhoneFamily(label: "iPhone 17", variants: ["", " Pro", " Pro Max"]),
            PhoneFamily(label: "iPhone 18", variants: ["", " Pro", " Pro Max"]),
            PhoneFamily(label: "iPhone Duo", variants: [""]), // Apple's first foldable, announced Sep 9 2026
        ],
        "Samsung": [
            PhoneFamily(label: "Galaxy S23", variants: ["", "+", " Ultra"]),
            PhoneFamily(label: "Galaxy S24", variants: ["", "+", " Ultra"]),
            PhoneFamily(label: "Galaxy S25", variants: ["", "+", " Ultra"]),
            PhoneFamily(label: "Galaxy Z Flip", variants: [""]),
            PhoneFamily(label: "Galaxy Z Fold", variants: [""]),
        ],
        "Google Pixel": [
            PhoneFamily(label: "Pixel 7", variants: ["", " Pro"]),
            PhoneFamily(label: "Pixel 8", variants: ["", " Pro"]),
            PhoneFamily(label: "Pixel 9", variants: ["", " Pro", " Pro XL"]),
            PhoneFamily(label: "Pixel 10", variants: ["", " Pro", " Pro XL"]),
        ],
    ]
}

import { VideoUpload } from "@/components/video/VideoUpload";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

export default function UploadPage() {
  return (
    <div className="container mx-auto px-4 py-12 max-w-4xl">
      <div className="mb-8 flex items-center">
        <Link href="/" className="text-muted-foreground hover:text-foreground transition-colors flex items-center text-sm font-medium">
          <ArrowLeft className="w-4 h-4 mr-2" /> Back to Home
        </Link>
      </div>
      
      <div className="mb-10 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 tracking-tight">Upload Video</h1>
        <p className="text-muted-foreground max-w-2xl mx-auto">
          Share your video with the world. Your video will be uploaded securely and processed for optimal streaming quality.
        </p>
      </div>
      
      <VideoUpload />
    </div>
  );
}
